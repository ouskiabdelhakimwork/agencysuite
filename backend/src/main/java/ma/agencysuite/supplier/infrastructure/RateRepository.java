package ma.agencysuite.supplier.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.supplier.domain.Rate;
import ma.agencysuite.supplier.domain.RateBasis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tarifs de l'agence courante (filtre @TenantId + RLS). */
public interface RateRepository extends JpaRepository<Rate, UUID>, JpaSpecificationExecutor<Rate> {

    /** Tarif de cette prestation ; vide s'il appartient a une autre prestation (ou a une autre agence). */
    Optional<Rate> findByIdAndProductId(UUID id, UUID productId);

    /** Tarifs non archives de la prestation. */
    List<Rate> findByProductIdAndArchivedAtIsNull(UUID productId);

    /**
     * Tarifs non archives de la meme prestation et de la meme base dont la periode a au moins un
     * jour commun avec {@code [from, to]} (dates incluses), autres que {@code self}. A appeler sous
     * le verrou de la prestation ({@link ProductRepository#findByIdForUpdate(UUID)}).
     */
    @Query("""
            SELECT r FROM Rate r
            WHERE r.productId = :productId AND r.basis = :basis AND r.archivedAt IS NULL
              AND r.validFrom <= :to AND r.validTo >= :from AND r.id <> :self
            ORDER BY r.validFrom, r.id
            """)
    List<Rate> findOverlapping(@Param("productId") UUID productId, @Param("basis") RateBasis basis,
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("self") UUID self);
}
