package ma.agencysuite.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsExactlyWithoutFloatingPointError() {
        Money total = Money.mad("0.1").plus(Money.mad("0.2"));

        assertThat(total).isEqualTo(Money.mad("0.3"));
        assertThat(total.amount()).isEqualByComparingTo("0.3");
    }

    @Test
    void normalizesScaleToFourDecimals() {
        assertThat(Money.mad("12").amount().scale()).isEqualTo(4);
        assertThat(Money.mad("12")).isEqualTo(Money.mad("12.0000"));
    }

    @Test
    void multipliesByQuantity() {
        assertThat(Money.mad("1250.50").times(3)).isEqualTo(Money.mad("3751.50"));
    }

    @Test
    void roundsToTwoDecimalsForMadHalfUp() {
        assertThat(Money.mad("10.005").roundedToMinorUnit()).isEqualByComparingTo(new BigDecimal("10.01"));
        assertThat(Money.mad("10.004").roundedToMinorUnit()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.mad("100").plus(Money.of("10", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MAD")
                .hasMessageContaining("EUR");
    }

    @Test
    void comparesAmountsInSameCurrency() {
        assertThat(Money.mad("5")).isLessThan(Money.mad("5.01"));
        assertThat(Money.mad("-1").isNegative()).isTrue();
        assertThat(Money.zero(Money.MAD).isZero()).isTrue();
    }
}
