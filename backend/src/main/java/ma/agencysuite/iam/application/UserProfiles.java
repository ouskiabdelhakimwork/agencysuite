package ma.agencysuite.iam.application;

import java.util.List;
import java.util.Optional;
import ma.agencysuite.iam.domain.PlatformUser;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.domain.UserRole;
import ma.agencysuite.iam.infrastructure.PlatformUserRepository;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.iam.infrastructure.UserRoleRepository;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Profil de l'utilisateur connecte (endpoints /me). Lit dans le perimetre de la requete, sans
 * jamais en ouvrir : l'utilisateur et l'agence viennent du jeton, jamais d'un parametre du client.
 */
@Service
public class UserProfiles {

    private final UserAccountRepository users;
    private final UserRoleRepository roles;
    private final PlatformUserRepository platformUsers;
    private final PlatformApi platform;
    private final TransactionTemplate readOnly;

    UserProfiles(UserAccountRepository users, UserRoleRepository roles, PlatformUserRepository platformUsers,
            PlatformApi platform, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.roles = roles;
        this.platformUsers = platformUsers;
        this.platform = platform;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** Profil de l'utilisateur courant de l'agence ; exige UserContext et le perimetre de l'agence. */
    public AgencyUserProfile currentAgencyUser() {
        CurrentUser current = UserContext.current().orElseThrow(UserRequiredException::new);
        AgencyId agencyId = TenantContext.requireAgency();
        String agencyName = platform.agencyName(agencyId)
                .orElseThrow(() -> new IllegalStateException("Agence courante illisible"));
        List<String> permissions = current.permissions().stream()
                .map(PermissionCode::code)
                .sorted()
                .toList();
        return readOnly.execute(status -> {
            UserAccount user = users.findById(current.userId())
                    .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));
            List<String> roleCodes = roles.findByUserIdOrderByRoleCode(user.getId()).stream()
                    .map(UserRole::getRoleCode)
                    .toList();
            return new AgencyUserProfile(user.getId(), user.getEmail(), user.getFullName(),
                    agencyId, agencyName, roleCodes, permissions);
        });
    }

    /** Profil d'un membre ACTIVE de l'editeur ; vide hors perimetre plateforme (RLS platform_only). */
    public Optional<PlatformUserProfile> platformUser(String sub) {
        return readOnly.execute(status -> platformUsers.findByExternalIdpId(sub)
                .filter(PlatformUser::isActive)
                .map(user -> new PlatformUserProfile(user.getId(), user.getEmail(), user.getFullName(),
                        user.getRole().name())));
    }
}
