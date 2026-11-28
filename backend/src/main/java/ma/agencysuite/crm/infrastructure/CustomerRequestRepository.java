package ma.agencysuite.crm.infrastructure;

import java.util.UUID;
import ma.agencysuite.crm.domain.CustomerRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Demandes de l'agence courante (filtre @TenantId + RLS). */
public interface CustomerRequestRepository extends JpaRepository<CustomerRequest, UUID>,
        JpaSpecificationExecutor<CustomerRequest> {
}
