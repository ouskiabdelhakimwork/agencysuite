package ma.agencysuite.iam.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.SystemRole;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.domain.UserRole;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.iam.infrastructure.UserRoleRepository;
import ma.agencysuite.platform.AgencyUserSettings;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gestion des utilisateurs de l'agence courante par son gerant (US-201) : liste, roles,
 * desactivation et reactivation. N'ouvre jamais de perimetre ; un utilisateur d'une autre agence est
 * invisible ({@link UserNotFoundException}, 404).
 *
 * <p>Regles :
 * <ul>
 *   <li>une agence garde toujours au moins un MANAGER actif ou invite ({@link LastManagerException}) ;</li>
 *   <li>on ne se desactive pas soi-meme ({@link CannotDisableSelfException}) ;</li>
 *   <li>la reactivation respecte le quota du palier ({@link UserQuotaExceededException}).</li>
 * </ul>
 *
 * <p>Desactiver : la base d'abord (DISABLED : l'acces est coupe tout de suite par le filtre de
 * securite), puis Keycloak (compte desactive, sessions fermees). Si Keycloak echoue, l'utilisateur
 * reste bloque chez nous, ce qui est l'effet voulu : {@code identityProviderSynced = false}, et un
 * nouvel appel resynchronise. Meme ordre pour la reactivation.
 */
@Service
public class UserAdministration {

    private static final Logger LOG = LoggerFactory.getLogger(UserAdministration.class);
    private static final String MANAGER = SystemRole.MANAGER.name();

    private final UserAccountRepository users;
    private final UserRoleRepository roles;
    private final PlatformApi platform;
    private final IdentityProviderAdmin identityProvider;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;

    UserAdministration(UserAccountRepository users, UserRoleRepository roles, PlatformApi platform,
            IdentityProviderAdmin identityProvider, AuditTrail audit, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.roles = roles;
        this.platform = platform;
        this.identityProvider = identityProvider;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** Utilisateur de l'agence, roles tries par code. */
    public record UserSummary(UUID id, String email, String fullName, UserStatus status, List<String> roles,
            Instant lastLoginAt) {
    }

    /** Utilisateurs de l'agence courante, par e-mail. */
    public List<UserSummary> list() {
        TenantContext.requireAgency();
        return readOnly.execute(status -> {
            List<UserAccount> accounts = users.findAllByOrderByEmail();
            Map<UUID, List<String>> rolesByUser = roles.findByUserIdIn(accounts.stream().map(UserAccount::getId).toList())
                    .stream()
                    .collect(Collectors.groupingBy(UserRole::getUserId,
                            Collectors.mapping(UserRole::getRoleCode, Collectors.toList())));
            return accounts.stream()
                    .map(user -> summary(user, rolesByUser.getOrDefault(user.getId(), List.of())))
                    .toList();
        });
    }

    /**
     * Remplace l'ensemble des roles de l'utilisateur. Une entree d'audit par role ajoute
     * (ROLE_ASSIGNED) ou retire (ROLE_REVOKED).
     */
    public UserSummary changeRoles(UUID userId, Set<SystemRole> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Au moins un role est requis");
        }
        AgencyId agencyId = TenantContext.requireAgency();
        return transaction.execute(status -> {
            UserAccount user = find(userId);
            List<UserRole> current = roles.findByUserIdOrderByRoleCode(userId);
            Set<String> wanted = requested.stream().map(SystemRole::name).collect(Collectors.toCollection(TreeSet::new));
            List<UserRole> removed = current.stream().filter(role -> !wanted.contains(role.getRoleCode())).toList();
            Set<String> existing = current.stream().map(UserRole::getRoleCode).collect(Collectors.toSet());
            List<String> added = wanted.stream().filter(code -> !existing.contains(code)).toList();

            boolean losesManager = removed.stream().anyMatch(role -> MANAGER.equals(role.getRoleCode()));
            if (losesManager && !user.isDisabled() && isLastManager(userId)) {
                throw new LastManagerException();
            }
            roles.deleteAll(removed);
            for (String code : added) {
                roles.save(new UserRole(userId, code));
            }
            roles.flush();
            removed.stream().map(UserRole::getRoleCode).sorted().forEach(code ->
                    audit.record(agencyId, AuditAction.ROLE_REVOKED, UserInvitations.USER, userId.toString(),
                            Map.of("role", code)));
            added.forEach(code -> audit.record(agencyId, AuditAction.ROLE_ASSIGNED, UserInvitations.USER,
                    userId.toString(), Map.of("role", code)));
            return summary(user, List.copyOf(wanted));
        });
    }

    /**
     * Desactive l'utilisateur. Deja desactive : rien n'est trace, Keycloak est resynchronise.
     *
     * @return vrai si Keycloak a ete mis a jour (compte desactive et sessions fermees)
     */
    public boolean disable(UUID userId) {
        AgencyId agencyId = TenantContext.requireAgency();
        CurrentUser current = UserContext.current().orElseThrow(UserRequiredException::new);
        if (current.userId().equals(userId)) {
            throw new CannotDisableSelfException();
        }
        String sub = transaction.execute(status -> {
            UserAccount user = find(userId);
            if (!user.isDisabled()) {
                if (hasRole(userId, MANAGER) && isLastManager(userId)) {
                    throw new LastManagerException();
                }
                user.disable();
                audit.record(agencyId, AuditAction.USER_DISABLED, UserInvitations.USER, userId.toString(), Map.of());
            }
            return user.getExternalIdpId();
        });
        return sync(sub, false);
    }

    /**
     * Reactive l'utilisateur (ACTIVE s'il s'est deja connecte, sinon INVITED). Deja actif ou
     * invite : rien n'est trace, Keycloak est resynchronise.
     *
     * @return vrai si Keycloak a ete mis a jour
     */
    public boolean enable(UUID userId) {
        AgencyId agencyId = TenantContext.requireAgency();
        AgencyUserSettings settings = platform.userSettings(agencyId)
                .orElseThrow(() -> new IllegalStateException("Agence courante illisible"));
        String sub = transaction.execute(status -> {
            UserAccount user = find(userId);
            if (user.isDisabled()) {
                if (!settings.allowsAnotherUser(users.countByStatusNot(UserStatus.DISABLED))) {
                    throw new UserQuotaExceededException();
                }
                user.enable();
                audit.record(agencyId, AuditAction.USER_ENABLED, UserInvitations.USER, userId.toString(), Map.of());
            }
            return user.getExternalIdpId();
        });
        return sync(sub, true);
    }

    private boolean sync(String sub, boolean enabled) {
        try {
            identityProvider.setEnabled(sub, enabled);
            if (!enabled) {
                identityProvider.logout(sub);
            }
            return true;
        } catch (IdentityAdminUnavailableException e) {
            LOG.warn("Fournisseur d'identite non synchronise pour sub={} (enabled={}) : {}", sub, enabled,
                    e.getMessage());
            return false;
        }
    }

    private UserAccount find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private boolean hasRole(UUID userId, String code) {
        return roles.findByUserIdOrderByRoleCode(userId).stream().anyMatch(role -> code.equals(role.getRoleCode()));
    }

    /** Vrai si aucun autre utilisateur non desactive de l'agence n'est MANAGER. */
    private boolean isLastManager(UUID userId) {
        return users.countOthersWithRole(MANAGER, UserStatus.DISABLED, userId) == 0;
    }

    private static UserSummary summary(UserAccount user, List<String> roleCodes) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(),
                roleCodes.stream().sorted().toList(), user.getLastLoginAt());
    }
}
