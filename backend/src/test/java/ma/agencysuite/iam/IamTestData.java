package ma.agencysuite.iam;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import ma.agencysuite.iam.domain.PlatformRole;
import ma.agencysuite.iam.domain.PlatformUser;
import ma.agencysuite.iam.domain.PlatformUserStatus;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.domain.UserRole;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.PlatformUserRepository;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.iam.infrastructure.UserRoleRepository;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Utilisateurs de test (donnees fictives, e-mails en @example.test). Les utilisateurs d'agence sont
 * ecrits dans le perimetre de leur agence (agency_id rempli par Hibernate), les membres de
 * l'editeur dans le perimetre plateforme. Les utilisateurs d'agence sont supprimes avec leur
 * agence par {@code PlatformTestData.deleteAgenciesWithSuffix}.
 */
@Component
public class IamTestData {

    private final UserAccountRepository users;
    private final UserRoleRepository roles;
    private final PlatformUserRepository platformUsers;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    IamTestData(UserAccountRepository users, UserRoleRepository roles, PlatformUserRepository platformUsers,
            TransactionTemplate transaction, EntityManager entityManager) {
        this.users = users;
        this.roles = roles;
        this.platformUsers = platformUsers;
        this.transaction = transaction;
        this.entityManager = entityManager;
    }

    /** Cree un utilisateur d'agence avec un sub aleatoire et les roles donnes ; renvoie son sub. */
    public String createUser(UUID agencyId, UserStatus status, String... roleCodes) {
        String sub = UUID.randomUUID().toString();
        createUser(agencyId, sub, status, roleCodes);
        return sub;
    }

    /** Cree un utilisateur d'agence avec ce sub et les roles donnes ; renvoie son identifiant. */
    public UUID createUser(UUID agencyId, String sub, UserStatus status, String... roleCodes) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx -> {
            UserAccount user = users.save(new UserAccount(sub, "user-" + sub.substring(0, 8) + "@example.test",
                    "Utilisateur " + sub.substring(0, 8), status));
            for (String role : roleCodes) {
                roles.save(new UserRole(user.getId(), role));
            }
            return user.getId();
        }));
    }

    /** Passe l'utilisateur d'agence au statut DISABLED. */
    public void disableUser(UUID agencyId, String sub) {
        TenantContext.runAsAgency(new AgencyId(agencyId), () -> transaction.executeWithoutResult(tx ->
                users.findAll().stream()
                        .filter(user -> user.getExternalIdpId().equals(sub))
                        .findFirst().orElseThrow()
                        .disable()));
    }

    /** Cree un membre de l'editeur (ADMIN) avec un sub aleatoire ; renvoie son sub. */
    public String createPlatformUser(PlatformUserStatus status) {
        String sub = UUID.randomUUID().toString();
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(tx -> platformUsers.save(
                new PlatformUser(sub, "staff-" + sub.substring(0, 8) + "@example.test", "Equipe Test",
                        PlatformRole.ADMIN, status))));
        return sub;
    }

    public void deletePlatformUser(String sub) {
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(tx -> entityManager
                .createNativeQuery("DELETE FROM platform_user WHERE external_idp_id = :sub")
                .setParameter("sub", sub)
                .executeUpdate()));
    }

    /** Identifiant (user_account.id) de l'utilisateur d'agence ayant ce sub. */
    public UUID userId(UUID agencyId, String sub) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx ->
                users.findAll().stream()
                        .filter(user -> user.getExternalIdpId().equals(sub))
                        .findFirst().orElseThrow()
                        .getId()));
    }

    /** Statut de l'utilisateur d'agence (lu dans le perimetre de son agence). */
    public UserStatus status(UUID agencyId, UUID userId) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx ->
                users.findById(userId).orElseThrow().getStatus()));
    }

    /** Utilisateur d'agence (lu dans le perimetre de son agence), vide s'il n'y existe pas. */
    public java.util.Optional<UserAccount> user(UUID agencyId, UUID userId) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx ->
                users.findById(userId)));
    }

    /** Codes des roles de l'utilisateur d'agence, tries. */
    public java.util.List<String> roles(UUID agencyId, UUID userId) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx ->
                roles.findByUserIdOrderByRoleCode(userId).stream().map(UserRole::getRoleCode).toList()));
    }

    /** Nombre d'utilisateurs de l'agence (tous statuts). */
    public long countUsers(UUID agencyId) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx -> users.count()));
    }

    /** Date de derniere connexion de l'utilisateur d'agence ayant ce sub. */
    public java.time.Instant lastLoginAt(UUID agencyId, String sub) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(tx ->
                users.findAll().stream()
                        .filter(user -> user.getExternalIdpId().equals(sub))
                        .findFirst().orElseThrow()
                        .getLastLoginAt()));
    }
}
