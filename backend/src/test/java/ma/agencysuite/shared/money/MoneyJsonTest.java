package ma.agencysuite.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Forme JSON des montants : {@code amount} en chaine en entree comme en sortie ; un nombre JSON est
 * refuse au lieu d'etre converti. Le refus en 400 par l'API est verifie dans {@code RateApiTest}.
 */
class MoneyJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void acceptsTheAmountAsAString() {
        MoneyJson read = JSON.readValue("{\"amount\": \"1250.50\", \"currency\": \"EUR\"}", MoneyJson.class);

        assertThat(read.amount()).isEqualTo("1250.50");
        assertThat(read.currency()).isEqualTo("EUR");
        assertThat(read.toPositiveMoney("MAD")).isEqualTo(Money.exact("1250.5", "EUR"));
    }

    @Test
    void theCurrencyIsOptionalAndDefaultsToTheOneGivenByTheCaller() {
        MoneyJson read = JSON.readValue("{\"amount\": \"95.00\"}", MoneyJson.class);

        assertThat(read.currency()).isNull();
        assertThat(read.toPositiveMoney("MAD")).isEqualTo(Money.exact("95", "MAD"));
    }

    @Test
    void refusesAJsonNumberInsteadOfConvertingIt() {
        assertThatThrownBy(() -> JSON.readValue("{\"amount\": 1250.5, \"currency\": \"EUR\"}", MoneyJson.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> JSON.readValue("{\"amount\": 95, \"currency\": \"EUR\"}", MoneyJson.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> JSON.readValue("{\"amount\": true}", MoneyJson.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void writesTheAmountAsAStringAtTheCurrencyScale() {
        assertThat(JSON.writeValueAsString(MoneyJson.from(Money.exact("1250", "EUR"))))
                .isEqualTo("{\"amount\":\"1250.00\",\"currency\":\"EUR\"}");
        assertThat(JSON.writeValueAsString(MoneyJson.from(Money.exact("1250", "JPY"))))
                .isEqualTo("{\"amount\":\"1250\",\"currency\":\"JPY\"}");
        assertThat(JSON.writeValueAsString(MoneyJson.from(Money.exact("1250", "TND"))))
                .isEqualTo("{\"amount\":\"1250.000\",\"currency\":\"TND\"}");
    }

    @Test
    void tooManyDecimalsAreRefusedWhenConverted() {
        MoneyJson read = JSON.readValue("{\"amount\": \"95.555\", \"currency\": \"EUR\"}", MoneyJson.class);

        assertThatThrownBy(() -> read.toPositiveMoney("MAD"))
                .isInstanceOf(InvalidMoneyException.class)
                .extracting(e -> ((InvalidMoneyException) e).getReason())
                .isEqualTo(InvalidMoneyException.Reason.TOO_MANY_DECIMALS);
    }
}
