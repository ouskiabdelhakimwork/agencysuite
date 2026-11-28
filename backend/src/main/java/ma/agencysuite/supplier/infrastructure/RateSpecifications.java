package ma.agencysuite.supplier.infrastructure;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.supplier.domain.Rate;
import ma.agencysuite.supplier.domain.RateBasis;
import org.springframework.data.jpa.domain.Specification;

/** Criteres de la liste des tarifs d'une prestation (l'agence est ajoutee par le filtre @TenantId). */
public final class RateSpecifications {

    private RateSpecifications() {
    }

    /**
     * @param archived vrai : seulement les tarifs archives
     * @param on si present : seulement les tarifs valables ce jour-la (dates incluses)
     */
    public static Specification<Rate> matching(UUID productId, RateBasis basis, LocalDate on, boolean archived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("productId"), productId));
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (basis != null) {
                predicates.add(cb.equal(root.get("basis"), basis));
            }
            if (on != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("validFrom"), on));
                predicates.add(cb.greaterThanOrEqualTo(root.get("validTo"), on));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
