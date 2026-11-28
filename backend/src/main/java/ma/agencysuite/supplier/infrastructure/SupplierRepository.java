package ma.agencysuite.supplier.infrastructure;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.supplier.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Fournisseurs de l'agence courante (filtre @TenantId + RLS). */
public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    /** Fournisseurs non archives ayant cet ICE, candidats au doublon probable. */
    List<Supplier> findTop10ByArchivedAtIsNullAndIceOrderByNameKeyAscIdAsc(String ice);

    /** Fournisseurs non archives ayant cet e-mail (deja normalise), candidats au doublon probable. */
    List<Supplier> findTop10ByArchivedAtIsNullAndEmailOrderByNameKeyAscIdAsc(String email);

    /** Fournisseurs non archives ayant ce telephone (E.164), candidats au doublon probable. */
    List<Supplier> findTop10ByArchivedAtIsNullAndPhoneOrderByNameKeyAscIdAsc(String phone);
}
