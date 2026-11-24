package ma.agencysuite.iam.infrastructure;

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
     * Utilisateur ayant ce sub et ce statut, avec ses permissions, en une seule requete
     * (user_account &rarr; user_role &rarr; system_role_permission). Une ligne par permission ;
     * une seule ligne avec permission nulle si l'utilisateur n'a aucun role ; aucune ligne si
     * l'utilisateur n'existe pas dans l'agence courante ou n'a pas ce statut.
     */
    @Query("""
            select new ma.agencysuite.iam.infrastructure.UserPermissionRow(u.id, p.id.permissionCode)
            from UserAccount u
            left join UserRole r on r.userId = u.id
            left join SystemRolePermission p on p.id.roleCode = r.roleCode
            where u.externalIdpId = :sub and u.status = :status
            """)
    List<UserPermissionRow> findWithPermissions(@Param("sub") String sub, @Param("status") UserStatus status);
}
