package ma.agencysuite.crm.infrastructure;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerRequest;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.crm.domain.RequestStatus;
import ma.agencysuite.shared.text.SearchKeys;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteres de recherche des demandes (l'agence est ajoutee par le filtre @TenantId, aussi dans la
 * sous-requete sur les clients).
 *
 * <p>{@code q} : {@code LIKE %q%} sur {@code search_key} (destination, apres le meme calcul
 * {@link SearchKeys#words} : sans accents ni tirets), ou sur {@code lower(customer.name)} par
 * sous-requete. Les caracteres {@code %} et {@code _} saisis sont pris a la lettre.
 * Periode de creation : {@code createdFrom} et {@code createdTo} inclus, journees UTC.
 * TODO(fuseau-agence): bornes calculees dans le fuseau de l'agence (Agency.timezone), pas en UTC.
 * TODO(etape-13b): recherche approchee (ADR-22).
 */
public final class CustomerRequestSpecifications {

    private record Criteria(Set<RequestStatus> statuses, UUID ownerUserId, UUID customerId, RequestChannel channel,
            LocalDate createdFrom, LocalDate createdTo, String q) {
    }

    private CustomerRequestSpecifications() {
    }

    /** Chaque critere est facultatif (vide ou nul : pas de filtre). */
    public static Specification<CustomerRequest> matching(Set<RequestStatus> statuses, UUID ownerUserId,
            UUID customerId, RequestChannel channel, LocalDate createdFrom, LocalDate createdTo, String q) {
        Criteria criteria = new Criteria(statuses, ownerUserId, customerId, channel, createdFrom, createdTo, q);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
                predicates.add(root.get("status").in(criteria.statuses()));
            }
            if (criteria.ownerUserId() != null) {
                predicates.add(cb.equal(root.get("ownerUserId"), criteria.ownerUserId()));
            }
            if (criteria.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), criteria.customerId()));
            }
            if (criteria.channel() != null) {
                predicates.add(cb.equal(root.get("channel"), criteria.channel()));
            }
            if (criteria.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(criteria.createdFrom())));
            }
            if (criteria.createdTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), startOfDay(criteria.createdTo().plusDays(1))));
            }
            String text = criteria.q() == null ? "" : criteria.q().strip();
            if (!text.isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                String key = SearchKeys.words(text);
                if (!key.isEmpty()) {
                    any.add(cb.like(root.get("searchKey"), SearchKeys.containsPattern(key), SearchKeys.likeEscape()));
                }
                Subquery<UUID> customer = query.subquery(UUID.class);
                Root<Customer> c = customer.from(Customer.class);
                customer.select(c.get("id")).where(
                        cb.equal(c.get("id"), root.get("customerId")),
                        cb.like(cb.lower(c.get("name")), SearchKeys.containsPattern(text.toLowerCase(Locale.ROOT)),
                                SearchKeys.likeEscape()));
                any.add(cb.exists(customer));
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
