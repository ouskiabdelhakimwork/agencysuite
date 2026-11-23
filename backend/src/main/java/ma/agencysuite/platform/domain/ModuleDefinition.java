package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.money.Money;

/**
 * Module du catalogue (table module). Nomme ModuleDefinition pour ne pas entrer
 * en conflit avec java.lang.Module. Les libelles affiches sont dans les fichiers
 * de traduction (cle module.&lt;CODE&gt;).
 */
@Entity
@Table(name = "module")
public class ModuleDefinition {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false)
    private short phase;

    @Column(name = "price_monthly", precision = 19, scale = 4)
    private BigDecimal priceMonthly;

    @Column(nullable = false, length = 3)
    private String currency;

    protected ModuleDefinition() {
        // pour JPA
    }

    public ModuleCode getCode() {
        return ModuleCode.valueOf(code);
    }

    public short getPhase() {
        return phase;
    }

    public Optional<Money> getPriceMonthly() {
        return Optional.ofNullable(priceMonthly).map(p -> Money.of(p, Currency.getInstance(currency)));
    }
}
