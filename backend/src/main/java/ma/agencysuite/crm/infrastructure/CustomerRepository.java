package ma.agencysuite.crm.infrastructure;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Clients de l'agence courante (filtre @TenantId + RLS). */
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    /** Clients non archives ayant cet e-mail (deja normalise), candidats au doublon probable. */
    List<Customer> findTop10ByArchivedAtIsNullAndEmailOrderByName(String email);

    /** Clients non archives ayant ce telephone (E.164), candidats au doublon probable. */
    List<Customer> findTop10ByArchivedAtIsNullAndPhoneOrderByName(String phone);

    /** Clients non archives ayant cet ICE, candidats au doublon probable. */
    List<Customer> findTop10ByArchivedAtIsNullAndIceOrderByName(String ice);
}
