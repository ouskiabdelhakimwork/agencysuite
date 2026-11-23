package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.persistence.BaseEntity;

/**
 * Abonnement d'une agence a un palier du socle. Une seule souscription active par
 * agence (index unique partiel en base). Facture hors outil en V1 (ADR-28).
 */
@Entity
@Table(name = "subscription")
public class Subscription extends BaseEntity {

    @Column(name = "agency_id", nullable = false, updatable = false)
    private UUID agencyId;

    @Column(name = "plan_code", nullable = false, length = 30)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected Subscription() {
        // pour JPA
    }

    public static Subscription start(UUID agencyId, String planCode, SubscriptionPeriod period, LocalDate startDate) {
        Subscription subscription = new Subscription();
        subscription.agencyId = Objects.requireNonNull(agencyId, "agencyId");
        subscription.planCode = Objects.requireNonNull(planCode, "planCode");
        subscription.period = Objects.requireNonNull(period, "period");
        subscription.startDate = Objects.requireNonNull(startDate, "startDate");
        subscription.status = SubscriptionStatus.ACTIVE;
        return subscription;
    }

    public void end(LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La date de fin precede la date de debut");
        }
        this.endDate = endDate;
        this.status = SubscriptionStatus.ENDED;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public SubscriptionPeriod getPeriod() {
        return period;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
