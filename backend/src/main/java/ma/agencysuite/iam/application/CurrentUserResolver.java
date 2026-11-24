package ma.agencysuite.iam.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ma.agencysuite.iam.domain.PlatformUser;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.PlatformUserRepository;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.iam.infrastructure.UserPermissionRow;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retrouve l'utilisateur a l'origine d'une requete a partir du {@code sub} de son jeton.
 *
 * <p>N'ouvre jamais de perimetre : il lit dans celui de l'appelant (le filtre de securite).
 * Dans le perimetre d'une agence, seuls les utilisateurs de cette agence sont trouvables (filtre
 * {@code @TenantId} + RLS) : le sub d'un utilisateur d'une autre agence ne donne rien. Sans le
 * perimetre attendu, rien. Lecture seule : aucune ecriture a chaque requete.
 */
@Service
public class CurrentUserResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentUserResolver.class);

    private final UserAccountRepository users;
    private final PlatformUserRepository platformUsers;
    private final TransactionTemplate readOnly;

    CurrentUserResolver(UserAccountRepository users, PlatformUserRepository platformUsers,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.platformUsers = platformUsers;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * Utilisateur ACTIVE de l'agence courante ayant ce sub, avec l'union des permissions de ses
     * roles. Vide s'il est inconnu dans cette agence, invite ou desactive, ou hors perimetre agence.
     */
    public Optional<CurrentUser> resolveAgencyUser(String sub) {
        if (sub == null || !(TenantContext.current().orElse(null) instanceof TenantScope.Agency)) {
            return Optional.empty();
        }
        List<UserPermissionRow> rows = readOnly.execute(status -> users.findWithPermissions(sub, UserStatus.ACTIVE));
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Set<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        for (UserPermissionRow row : rows) {
            if (row.permissionCode() != null) {
                // Ferme par defaut : une permission inconnue du code n'est pas accordee.
                PermissionCode.fromCode(row.permissionCode()).ifPresentOrElse(permissions::add,
                        () -> LOG.warn("Permission inconnue ignoree : {}", row.permissionCode()));
            }
        }
        return Optional.of(new CurrentUser(rows.getFirst().userId(), permissions));
    }

    /** Vrai si un membre ACTIVE de l'editeur a ce sub. Faux hors perimetre plateforme. */
    public boolean isActivePlatformUser(String sub) {
        if (sub == null || !(TenantContext.current().orElse(null) instanceof TenantScope.Platform)) {
            return false;
        }
        Boolean active = readOnly.execute(status -> platformUsers.findByExternalIdpId(sub)
                .map(PlatformUser::isActive)
                .orElse(false));
        return Boolean.TRUE.equals(active);
    }
}
