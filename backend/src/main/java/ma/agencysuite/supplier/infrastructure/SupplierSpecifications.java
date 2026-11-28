package ma.agencysuite.supplier.infrastructure;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.text.SearchKeys;
import ma.agencysuite.supplier.domain.Supplier;
import ma.agencysuite.supplier.domain.SupplierType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteres de recherche des fournisseurs (l'agence est ajoutee par le filtre @TenantId).
 *
 * <p>{@code q} : {@code LIKE %q%} sur {@code name_key} (apres la meme normalisation de {@code q} :
 * minuscules, sans accents), et sans tenir compte de la casse sur l'e-mail, le telephone, l'ICE et
 * la ville ; si {@code q} est un numero valide, egalite aussi sur sa forme E.164
 * ({@code 0524000000} trouve {@code +212524000000}). {@code %} et {@code _} sont pris a la lettre.
 * {@code city} : egalite sans tenir compte de la casse.
 * TODO(etape-13b): recherche approchee et translitteration latin / arabe (ADR-22).
 */
public final class SupplierSpecifications {

    private SupplierSpecifications() {
    }

    public static Specification<Supplier> matching(String q, SupplierType type, String city, boolean archived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            String cityText = city == null ? "" : city.strip();
            if (!cityText.isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("city")), cityText.toLowerCase(Locale.ROOT)));
            }
            String text = q == null ? "" : q.strip();
            if (!text.isEmpty()) {
                char escape = SearchKeys.likeEscape();
                List<Predicate> any = new ArrayList<>();
                String key = SearchKeys.normalize(text);
                if (!key.isEmpty()) {
                    any.add(cb.like(root.get("nameKey"), SearchKeys.containsPattern(key), escape));
                }
                String lower = SearchKeys.containsPattern(text.toLowerCase(Locale.ROOT));
                for (String field : List.of("email", "phone", "ice", "city")) {
                    any.add(cb.like(cb.lower(root.get(field)), lower, escape));
                }
                PhoneNumbers.toE164(text).ifPresent(e164 -> any.add(cb.equal(root.get("phone"), e164)));
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
