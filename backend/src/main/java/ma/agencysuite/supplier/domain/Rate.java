package ma.agencysuite.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.money.Money;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;

/**
 * Tarif d'achat d'une prestation : prix pour une base (single, double, par personne...) valable
 * du {@code validFrom} au {@code validTo} inclus, dans la devise du contrat.
 *
 * <p>Regles portees par l'entite : periode {@code validTo >= validFrom} (aussi verifie en base) ;
 * montant strictement positif ; une periode passee est acceptee (saisie d'historique). Le
 * non-chevauchement avec les autres tarifs de la meme prestation et de la meme base est une regle
 * du cas d'usage (sous verrou de la prestation). Pas de suppression : archivage et restauration.
 */
@Entity
@Table(name = "rate")
public class Rate extends AuthoredAgencyEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RateBasis basis;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "purchase_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchaseAmount;

    @Column(name = "purchase_currency", nullable = false, length = 3)
    private String purchaseCurrency;

    @Column(length = 100)
    private String label;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Rate() {
        // pour JPA
    }

    public static Rate create(UUID productId, RateData data) {
        Rate rate = new Rate();
        rate.productId = Objects.requireNonNull(productId, "productId");
        rate.update(data);
        return rate;
    }

    /** Remplace les champs modifiables (pas la prestation). */
    public void update(RateData data) {
        LocalDate from = Objects.requireNonNull(data.validFrom(), "validFrom");
        LocalDate to = Objects.requireNonNull(data.validTo(), "validTo");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Periode invalide : validTo avant validFrom");
        }
        Money purchase = Objects.requireNonNull(data.purchase(), "purchase").requirePositive();
        this.basis = Objects.requireNonNull(data.basis(), "basis");
        this.validFrom = from;
        this.validTo = to;
        this.purchaseAmount = purchase.amount();
        this.purchaseCurrency = purchase.currency().getCurrencyCode();
        this.label = Texts.trimToNull(data.label());
    }

    /** Vrai si les deux periodes ont au moins un jour commun (dates incluses). */
    public static boolean overlaps(LocalDate fromA, LocalDate toA, LocalDate fromB, LocalDate toB) {
        return !fromA.isAfter(toB) && !fromB.isAfter(toA);
    }

    /** Vrai si le tarif est valable ce jour-la (dates incluses). */
    public boolean isValidOn(LocalDate day) {
        return !day.isBefore(validFrom) && !day.isAfter(validTo);
    }

    /** @return vrai si le tarif vient d'etre archive (faux s'il l'etait deja) */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** @return vrai si le tarif vient d'etre restaure (faux s'il ne l'etait pas) */
    public boolean restore() {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        return true;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getProductId() {
        return productId;
    }

    public RateBasis getBasis() {
        return basis;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    /** Prix d'achat exact, dans la devise du tarif. */
    public Money getPurchase() {
        return Money.of(purchaseAmount, Currency.getInstance(purchaseCurrency));
    }

    public String getLabel() {
        return label;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
