package ma.agencysuite.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

    // Montants saisis (etape 5, lot 3) : refus au lieu d'arrondi.

    @Test
    void createsAnExactAmountInAKnownCurrency() {
        Money money = Money.exact("1250.5", "EUR");

        assertThat(money.amount()).isEqualByComparingTo("1250.5");
        assertThat(money.currency().getCurrencyCode()).isEqualTo("EUR");
        assertThat(money.requirePositive()).isSameAs(money);
    }

    @Test
    void refusesAnUnknownOrLowerCaseCurrency() {
        assertReason(() -> Money.exact("10", "ZZZ"), InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        assertReason(() -> Money.exact("10", "eur"), InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        assertReason(() -> Money.exact("10", null), InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        // Pseudo-devise ISO 4217 sans decimales definies (or)
        assertReason(() -> Money.exact("10", "XAU"), InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        assertThat(Money.isKnownCurrency("MAD")).isTrue();
        assertThat(Money.isKnownCurrency("mad")).isFalse();
    }

    @Test
    void refusesAZeroOrNegativeAmountWhereAPositiveOneIsRequired() {
        assertReason(() -> Money.exact("0", "MAD").requirePositive(), InvalidMoneyException.Reason.NOT_POSITIVE);
        assertReason(() -> Money.exact("0.00", "MAD").requirePositive(), InvalidMoneyException.Reason.NOT_POSITIVE);
        assertReason(() -> Money.exact("-5", "MAD").requirePositive(), InvalidMoneyException.Reason.NOT_POSITIVE);
    }

    @Test
    void refusesMoreDecimalsThanTheCurrencyAllowsWithoutRounding() {
        assertReason(() -> Money.exact("95.555", "MAD"), InvalidMoneyException.Reason.TOO_MANY_DECIMALS);
        assertReason(() -> Money.exact("95.555", "EUR"), InvalidMoneyException.Reason.TOO_MANY_DECIMALS);
        assertReason(() -> Money.exact("1250.5", "JPY"), InvalidMoneyException.Reason.TOO_MANY_DECIMALS);
        assertReason(() -> Money.exact("1.2345", "TND"), InvalidMoneyException.Reason.TOO_MANY_DECIMALS);

        assertThat(Money.exact("95.55", "EUR").amount()).isEqualByComparingTo("95.55");
        assertThat(Money.exact("1250", "JPY").amount()).isEqualByComparingTo("1250");
        assertThat(Money.exact("1.234", "TND").amount()).isEqualByComparingTo("1.234");
        assertThat(Money.exact("1.234", "KWD").amount()).isEqualByComparingTo("1.234");
    }

    @Test
    void trailingZerosAreNotExtraDecimals() {
        assertThat(Money.exact("95.500", "EUR").amount()).isEqualByComparingTo("95.5");
        assertThat(Money.exact("1250.00", "JPY").amount()).isEqualByComparingTo("1250");
    }

    @Test
    void refusesMoreThanFifteenIntegerDigitsAndMalformedText() {
        assertThat(Money.exact("999999999999999.99", "MAD").amount()).isEqualByComparingTo("999999999999999.99");
        assertReason(() -> Money.exact("1000000000000000", "MAD"), InvalidMoneyException.Reason.TOO_LARGE);
        for (String malformed : new String[] {"", " 10", "1e3", "1,5", "1 000", "+5", ".5", "5.", "abc", null}) {
            assertReason(() -> Money.exact(malformed, "MAD"), InvalidMoneyException.Reason.MALFORMED_AMOUNT);
        }
    }

    @Test
    void formatsTheAmountAtTheCurrencyScale() {
        assertThat(Money.exact("1250", "EUR").toPlainAmount()).isEqualTo("1250.00");
        assertThat(Money.exact("1250.5", "MAD").toPlainAmount()).isEqualTo("1250.50");
        assertThat(Money.exact("1250", "JPY").toPlainAmount()).isEqualTo("1250");
        assertThat(Money.exact("1250", "TND").toPlainAmount()).isEqualTo("1250.000");
        assertThat(Money.exact("0.1", "SAR").toPlainAmount()).isEqualTo("0.10");
    }

    @Test
    void formattingNeverRoundsAComputedAmount() {
        // Resultat d'un calcul (4 decimales internes) : toutes les decimales significatives restent.
        assertThat(Money.mad("10").times(new BigDecimal("0.3333")).toPlainAmount()).isEqualTo("3.333");
    }

    @Test
    void comparesExactAmounts() {
        assertThat(Money.exact("95.50", "EUR")).isEqualTo(Money.exact("95.5", "EUR"));
        assertThat(Money.exact("95.50", "EUR")).isLessThan(Money.exact("95.51", "EUR"));
        assertThat(Money.exact("95.50", "EUR")).isNotEqualTo(Money.exact("95.50", "USD"));
    }

    @Test
    void noFloatingPointTypeAnywhereInTheMoneyApi() {
        Set<Class<?>> floating = Set.of(double.class, float.class, Double.class, Float.class);
        for (Method method : Money.class.getDeclaredMethods()) {
            assertThat(method.getReturnType()).as(method.getName()).isNotIn(floating);
            assertThat(method.getParameterTypes()).as(method.getName()).doesNotContainAnyElementsOf(floating);
        }
        for (Constructor<?> constructor : Money.class.getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes()).doesNotContainAnyElementsOf(floating);
        }
        for (Field field : Money.class.getDeclaredFields()) {
            assertThat(field.getType()).as(field.getName()).isNotIn(floating);
        }
    }

    private static void assertReason(ThrowingCallable call, InvalidMoneyException.Reason reason) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidMoneyException.class)
                .extracting(e -> ((InvalidMoneyException) e).getReason())
                .isEqualTo(reason);
    }
}
