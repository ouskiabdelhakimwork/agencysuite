package ma.agencysuite.crm.infrastructure;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ma.agencysuite.crm.domain.CustomerTraveler;
import ma.agencysuite.crm.domain.NameKeys;
import ma.agencysuite.crm.domain.Traveler;
import ma.agencysuite.shared.contact.PhoneNumbers;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteres de recherche des voyageurs (l'agence est ajoutee par le filtre @TenantId).
 *
 * <p>{@code q} : {@code LIKE %q%} sur {@code name_key} (apres la meme normalisation de {@code q} :
 * minuscules, sans accents, espaces reduits), sur les noms arabes, sur l'e-mail et le telephone ;
 * si {@code q} est un numero valide, egalite aussi sur sa forme E.164. Les caracteres {@code %} et
 * {@code _} saisis sont pris a la lettre. La date de naissance n'est jamais un critere.
 * TODO(etape-13b): recherche approchee et translitteration latin / arabe (ADR-22).
 */
public final class TravelerSpecifications {

    private static final char ESCAPE = '\\';

    private TravelerSpecifications() {
    }

    public static Specification<Traveler> matching(String q, UUID customerId, boolean archived) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (customerId != null) {
                Subquery<UUID> linked = query.subquery(UUID.class);
                Root<CustomerTraveler> link = linked.from(CustomerTraveler.class);
                linked.select(link.get("travelerId"))
                        .where(cb.equal(link.get("customerId"), customerId),
                                cb.equal(link.get("travelerId"), root.get("id")));
                predicates.add(cb.exists(linked));
            }
            String text = q == null ? "" : q.strip();
            if (!text.isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                String key = NameKeys.normalize(text);
                if (!key.isEmpty()) {
                    any.add(cb.like(root.get("nameKey"), contains(key), ESCAPE));
                }
                String lower = contains(text.toLowerCase(Locale.ROOT));
                any.add(cb.like(root.get("lastNameAr"), contains(text), ESCAPE));
                any.add(cb.like(root.get("firstNameAr"), contains(text), ESCAPE));
                any.add(cb.like(cb.lower(root.get("email")), lower, ESCAPE));
                any.add(cb.like(root.get("phone"), lower, ESCAPE));
                PhoneNumbers.toE164(text).ifPresent(e164 -> any.add(cb.equal(root.get("phone"), e164)));
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String contains(String value) {
        return "%" + CustomerSpecifications.escapeLike(value) + "%";
    }
}
