package ma.agencysuite.supplier.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.supplier.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Prestations de l'agence courante (filtre @TenantId + RLS). */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /**
     * Prestation verrouillee ({@code SELECT ... FOR UPDATE}) jusqu'a la fin de la transaction :
     * serialise les ecritures de tarifs d'une meme prestation (controle de chevauchement).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Nombre de prestations non archivees de chacun de ces fournisseurs : lignes
     * {@code [supplierId, count]} ; un fournisseur sans prestation n'a pas de ligne.
     */
    @Query("""
            SELECT p.supplierId, count(p) FROM Product p
            WHERE p.supplierId IN :supplierIds AND p.archivedAt IS NULL
            GROUP BY p.supplierId
            """)
    List<Object[]> countActiveBySupplier(@Param("supplierIds") Collection<UUID> supplierIds);
}
