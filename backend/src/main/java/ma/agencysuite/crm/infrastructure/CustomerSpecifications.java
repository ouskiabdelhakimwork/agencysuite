package ma.agencysuite.crm.infrastructure;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.shared.contact.PhoneNumbers;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteres de recherche des clients (l'agence est ajoutee par le filtre @TenantId).
 *
 * <p>{@code q} : {@code lower(...) LIKE %q%} sur le nom, l'e-mail, le telephone et l'ICE ; si
 * {@code q} est un numero valide, egalite aussi sur sa forme E.164 ({@code 0522000000} trouve
 * {@code +212522000000}). Les caracteres {@code %} et {@code _} saisis sont pris a la lettre.
 * TODO(etape-13b): recherche insensible aux accents et approchee (ADR-22, recherche globale).
 */
public final class CustomerSpecifications {

    private static final char ESCAPE = '\\';

    private CustomerSpecifications() {
    }

    public static Specification<Customer> matching(String q, CustomerType type, UUID ownerUserId, boolean archived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), ownerUserId));
            }
            String text = q == null ? "" : q.strip();
            if (!text.isEmpty()) {
                String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> any = new ArrayList<>();
                for (String field : List.of("name", "email", "phone", "ice")) {
                    any.add(cb.like(cb.lower(root.get(field)), pattern, ESCAPE));
                }
                PhoneNumbers.toE164(text).ifPresent(e164 -> any.add(cb.equal(root.get("phone"), e164)));
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
