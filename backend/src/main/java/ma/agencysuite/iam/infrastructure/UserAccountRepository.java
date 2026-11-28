package ma.agencysuite.iam.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Utilisateurs de l'agence courante (filtre @TenantId + RLS). */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Utilisateur ayant ce sub et l'un de ces statuts, avec ses permissions, en une seule requete
     * (user_account &rarr; user_role &rarr; system_role_permission). Une ligne par permission ;
     * une seule ligne avec permission nulle si l'utilisateur n'a aucun role ; aucune ligne si
     * l'utilisateur n'existe pas dans l'agence courante ou n'a pas l'un de ces statuts.
     */
    @Query("""
            select new ma.agencysuite.iam.infrastructure.UserPermissionRow(u.id, p.id.permissionCode)
            from UserAccount u
            left join UserRole r on r.userId = u.id
            left join SystemRolePermission p on p.id.roleCode = r.roleCode
            where u.externalIdpId = :sub and u.status in :statuses
            """)
    List<UserPermissionRow> findWithPermissions(@Param("sub") String sub,
            @Param("statuses") Collection<UserStatus> statuses);

    /** Utilisateurs de l'agence courante, par e-mail. */
    List<UserAccount> findAllByOrderByEmail();

    /** Utilisateurs de l'agence courante ayant l'un de ces statuts, par nom (casse ignoree), puis identifiant. */
    @Query("""
            select u from UserAccount u
            where u.status in :statuses
            order by lower(u.fullName), u.id
            """)
    List<UserAccount> findByStatusInOrderByName(@Param("statuses") Collection<UserStatus> statuses);

    /** Vrai si un utilisateur de l'agence courante a deja cet e-mail (casse ignoree, comme l'index unique). */
    boolean existsByEmailIgnoreCase(String email);

    /** Nombre d'utilisateurs de l'agence courante qui ne sont pas dans ce statut. */
    long countByStatusNot(UserStatus status);

    /**
     * Nombre d'utilisateurs de l'agence courante ayant ce role, hors statut exclu et hors
     * l'utilisateur donne (regle du dernier gerant).
     */
    @Query("""
            select count(u) from UserAccount u
            join UserRole r on r.userId = u.id
            where r.roleCode = :role and u.status <> :excluded and u.id <> :userId
            """)
    long countOthersWithRole(@Param("role") String role, @Param("excluded") UserStatus excluded,
            @Param("userId") UUID userId);
}
