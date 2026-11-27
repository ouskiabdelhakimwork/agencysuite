package ma.agencysuite.crm.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.crm.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contacts de l'agence courante (filtre @TenantId + RLS). */
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /** Contacts du client : le principal d'abord, puis par nom. */
    List<Contact> findByCustomerIdOrderByPrimaryDescFullNameAsc(UUID customerId);

    /** Contact de ce client ; vide s'il appartient a un autre client (ou a une autre agence). */
    Optional<Contact> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Contact> findByCustomerIdAndPrimaryTrue(UUID customerId);

    boolean existsByCustomerId(UUID customerId);
}
