package ma.agencysuite.crm.infrastructure;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerTraveler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Liens voyageur &harr; client de l'agence courante (filtre @TenantId + RLS). */
public interface CustomerTravelerRepository extends JpaRepository<CustomerTraveler, CustomerTraveler.Key> {

    /** Clients rattaches au voyageur (archives compris), par nom puis identifiant. */
    @Query("""
            SELECT c FROM Customer c, CustomerTraveler l
            WHERE l.customerId = c.id AND l.travelerId = :travelerId
            ORDER BY lower(c.name), c.id
            """)
    List<Customer> findCustomersOfTraveler(@Param("travelerId") UUID travelerId);
}
