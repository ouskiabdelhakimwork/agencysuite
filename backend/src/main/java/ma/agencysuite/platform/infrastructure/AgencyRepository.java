package ma.agencysuite.platform.infrastructure;

import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.platform.domain.Agency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyRepository extends JpaRepository<Agency, UUID> {

    Optional<Agency> findBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);
}
