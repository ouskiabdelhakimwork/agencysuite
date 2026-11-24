package ma.agencysuite.shared.tenant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantProbeRepository extends JpaRepository<TenantProbe, UUID> {
}
