package ma.agencysuite.supplier.infrastructure;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.shared.text.SearchKeys;
import ma.agencysuite.supplier.domain.Product;
import ma.agencysuite.supplier.domain.ProductType;
import ma.agencysuite.supplier.domain.Supplier;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteres de recherche des prestations (l'agence est ajoutee par le filtre @TenantId).
 *
 * <p>{@code q} : {@code LIKE %q%} sur {@code search_key} (nom dans toutes ses langues, par mots :
 * minuscules, sans accents, traits d'union ramenes a une espace), apres le meme calcul sur
 * {@code q} ({@link SearchKeys#words(String)}). {@code supplierArchived} : faux, seulement les
 * prestations des fournisseurs non archives ; vrai, seulement celles des fournisseurs archives
 * (meme logique que {@code archived}).
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(String q, UUID supplierId, ProductType type, boolean archived,
            boolean supplierArchived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplierId"), supplierId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            Subquery<UUID> suppliers = query.subquery(UUID.class);
            Root<Supplier> supplier = suppliers.from(Supplier.class);
            suppliers.select(supplier.get("id")).where(
                    cb.equal(supplier.get("id"), root.get("supplierId")),
                    supplierArchived ? cb.isNotNull(supplier.get("archivedAt")) : cb.isNull(supplier.get("archivedAt")));
            predicates.add(cb.exists(suppliers));
            String key = SearchKeys.words(q);
            if (!key.isEmpty()) {
                predicates.add(cb.like(root.get("searchKey"), SearchKeys.containsPattern(key), SearchKeys.likeEscape()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
