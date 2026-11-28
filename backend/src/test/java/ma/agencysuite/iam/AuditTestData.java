package ma.agencysuite.iam;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lecture du journal d'audit pour les tests, en SQL direct dans le perimetre plateforme (la RLS y
 * laisse tout voir) : les tests verifient ce qui est reellement en base, colonne par colonne.
 */
@Component
public class AuditTestData {

    /** Ligne de audit_log ; {@code details} est le JSON tel que stocke. */
    public record Row(UUID agencyId, String actorType, UUID actorId, String action, String entityType,
            String entityId, String details, String ip, Instant occurredAt) {
    }

    private static final String COLUMNS =
            "agency_id, actor_type, actor_id, action, entity_type, entity_id, details::text, ip, occurred_at";

    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    AuditTestData(TransactionTemplate transaction, EntityManager entityManager) {
        this.transaction = transaction;
        this.entityManager = entityManager;
    }

    /**
     * Entrees de l'agence, par {@code occurred_at} puis identifiant (meme ordre total que l'API, a
     * l'envers). Fiable entre transactions successives ; pour des entrees ecrites dans la meme
     * transaction (meme {@code occurred_at} possible), l'ordre n'est pas celui d'ecriture : les tests
     * ne l'affirment pas.
     */
    public List<Row> entries(UUID agencyId) {
        return query("SELECT " + COLUMNS + " FROM audit_log WHERE agency_id = :agency ORDER BY occurred_at, id",
                agencyId);
    }

    /** Toutes les entrees du journal (toutes agences). */
    public List<Row> allEntries() {
        return query("SELECT " + COLUMNS + " FROM audit_log ORDER BY occurred_at, id", null);
    }

    @SuppressWarnings("unchecked")
    private List<Row> query(String sql, UUID agencyId) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> {
            var query = entityManager.createNativeQuery(sql);
            if (agencyId != null) {
                query.setParameter("agency", agencyId);
            }
            return ((List<Object[]>) query.getResultList()).stream()
                    .map(r -> new Row((UUID) r[0], (String) r[1], (UUID) r[2], (String) r[3], (String) r[4],
                            (String) r[5], (String) r[6], (String) r[7], toInstant(r[8])))
                    .toList();
        }));
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case java.time.OffsetDateTime odt -> odt.toInstant();
            case java.sql.Timestamp ts -> ts.toInstant();
            default -> throw new IllegalStateException("Type inattendu : " + value.getClass());
        };
    }
}
