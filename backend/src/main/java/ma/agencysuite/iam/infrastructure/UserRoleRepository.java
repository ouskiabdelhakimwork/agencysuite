package ma.agencysuite.iam.infrastructure;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.iam.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

/** Roles des utilisateurs de l'agence courante (filtre @TenantId + RLS). */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserIdOrderByRoleCode(UUID userId);
}
