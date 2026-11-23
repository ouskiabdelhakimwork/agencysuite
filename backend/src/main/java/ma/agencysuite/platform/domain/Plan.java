package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.OptionalInt;
import ma.agencysuite.shared.money.Money;

/** Palier du socle (Starter, Pro, Business). Donnee de reference, lecture seule. */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @Column(length = 30)
    private String code;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "storage_quota_gb", nullable = false)
    private int storageQuotaGb;

    @Column(name = "price_monthly", precision = 19, scale = 4)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", precision = 19, scale = 4)
    private BigDecimal priceYearly;

    @Column(nullable = false, length = 3)
    private String currency;

    protected Plan() {
        // pour JPA
    }

    public String getCode() {
        return code;
    }

    /** Vide si le nombre d'utilisateurs est illimite. */
    public OptionalInt getMaxUsers() {
        return maxUsers == null ? OptionalInt.empty() : OptionalInt.of(maxUsers);
    }

    public int getStorageQuotaGb() {
        return storageQuotaGb;
    }

    /** Vide tant que le prix n'est pas defini. */
    public Optional<Money> getPriceMonthly() {
        return Optional.ofNullable(priceMonthly).map(p -> Money.of(p, Currency.getInstance(currency)));
    }

    public Optional<Money> getPriceYearly() {
        return Optional.ofNullable(priceYearly).map(p -> Money.of(p, Currency.getInstance(currency)));
    }
}
