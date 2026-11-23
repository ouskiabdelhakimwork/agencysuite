package ma.agencysuite.platform.infrastructure;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.AgencyModuleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyModuleRepository extends JpaRepository<AgencyModule, AgencyModuleId> {

    List<AgencyModule> findByIdAgencyIdAndEnabledTrue(UUID agencyId);
}
