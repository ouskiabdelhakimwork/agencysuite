package ma.agencysuite.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.id.Uuids;
import ma.agencysuite.shared.security.Actor;
import ma.agencysuite.shared.security.ActorType;
import ma.agencysuite.shared.tenant.AgencyId;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entree du journal d'audit : qui, quoi, sur quoi, quand, depuis quelle adresse. Ajout seul :
 * {@code @Immutable} (Hibernate n'emet jamais d'UPDATE) et, en base, ni UPDATE ni DELETE pour le
 * role applicatif (migration V5).
 *
 * <p><b>Exception a la regle multi-tenant</b> : cette entite n'herite pas de
 * {@code AgencyScopedEntity}. Une action du back-office s'execute dans le perimetre plateforme et
 * doit ecrire une entree pour l'agence ciblee ; avec {@code @TenantId}, Hibernate y mettrait
 * l'identifiant fictif de la plateforme. L'agence est donc explicite, et :
 * <ul>
 *   <li>la Row Level Security reste la barriere : en perimetre agence, lecture et ecriture
 *       limitees a l'agence courante ; en perimetre plateforme, ecriture pour toute agence ;</li>
 *   <li>Hibernate ne filtrant pas cette entite, toute lecture filtre en plus explicitement sur
 *       l'agence courante (double barriere, voir {@code AuditEntryRepository}).</li>
 * </ul>
 *
 * <p>{@code details} ne contient que des codes techniques, jamais de donnee personnelle ;
 * {@code ip} est une donnee personnelle, jamais journalisee ni renvoyee par l'API dans ce lot.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "agency_id", nullable = false, updatable = false)
    private UUID agencyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 100)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEntry() {
        // pour JPA
    }

    public AuditEntry(AgencyId agencyId, Actor actor, AuditAction action, String entityType, String entityId,
            Map<String, Object> details, String ip, Instant occurredAt) {
        this.id = Uuids.v7();
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId").value();
        this.actorType = Objects.requireNonNull(actor, "actor").type();
        this.actorId = actor.id();
        this.action = Objects.requireNonNull(action, "action");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.entityId = entityId;
        this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
        this.ip = ip;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public UUID getId() {
        return id;
    }

    public AgencyId getAgencyId() {
        return new AgencyId(agencyId);
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getIp() {
        return ip;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
