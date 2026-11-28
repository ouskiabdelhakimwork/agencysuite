package ma.agencysuite.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.security.ActorContext;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

/**
 * Rattachement d'un voyageur a un client de la meme agence (table {@code customer_traveler}).
 *
 * <p>Ni version, ni date de modification : un lien se cree ou se supprime (suppression reelle).
 * Cle composee {@code (customer_id, traveler_id)} : l'entite n'herite donc pas de
 * {@code AgencyScopedEntity} (identifiant UUID unique, version), mais porte le meme
 * {@code @TenantId} sur {@code agency_id} : Hibernate le remplit avec l'agence courante et filtre
 * toutes les requetes. Seconde barriere : RLS et cles etrangeres composees avec {@code agency_id}
 * vers {@code customer} et {@code traveler} (migration V8).
 */
@Entity
@Table(name = "customer_traveler")
@IdClass(CustomerTraveler.Key.class)
public class CustomerTraveler implements Persistable<CustomerTraveler.Key> {

    /** Cle composee (classe et non record : exigence de JPA pour {@code @IdClass}). */
    public static class Key implements Serializable {

        private UUID customerId;
        private UUID travelerId;

        protected Key() {
            // pour JPA
        }

        public Key(UUID customerId, UUID travelerId) {
            this.customerId = Objects.requireNonNull(customerId, "customerId");
            this.travelerId = Objects.requireNonNull(travelerId, "travelerId");
        }

        public UUID getCustomerId() {
            return customerId;
        }

        public UUID getTravelerId() {
            return travelerId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(customerId, key.customerId)
                    && Objects.equals(travelerId, key.travelerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, travelerId);
        }
    }

    @TenantId
    @Column(name = "agency_id", nullable = false, updatable = false)
    private UUID agencyId;

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Id
    @Column(name = "traveler_id", nullable = false, updatable = false)
    private UUID travelerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Identifiant fourni par l'appelant : Spring Data ne peut pas deviner qu'il est nouveau. */
    @Transient
    private boolean isNew = true;

    protected CustomerTraveler() {
        // pour JPA
    }

    public CustomerTraveler(UUID customerId, UUID travelerId) {
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.travelerId = Objects.requireNonNull(travelerId, "travelerId");
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        createdBy = ActorContext.current().id();
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public Key getId() {
        return new Key(customerId, travelerId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getTravelerId() {
        return travelerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
