package ma.agencysuite.shared.money;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Forme JSON d'un montant, en entree comme en sortie : {@code {"amount": "1250.00", "currency": "EUR"}}.
 *
 * <p>{@code amount} est <b>toujours une chaine</b> : exact cote Angular (pas de nombre flottant
 * JavaScript). En entree, un nombre JSON ({@code 1250.5}) est refuse (400 {@code malformed-request},
 * corps illisible) au lieu d'etre converti. La chaine est ensuite verifiee par
 * {@link Money#exact(String, String)} : au plus 15 chiffres avant la virgule, pas plus de decimales
 * que la devise n'en autorise, <b>jamais d'arrondi</b> (400 {@code validation-failed}).
 *
 * <p>{@code currency} : code ISO 4217 en majuscules. En entree il peut etre facultatif : c'est
 * l'appelant qui decide de la devise par defaut (par exemple celle du fournisseur pour un tarif).
 *
 * @param amount montant decimal en chaine, sans exposant ni separateur de milliers
 * @param currency code ISO 4217
 */
@Schema(name = "Money", description = "Montant exact dans une devise. amount est une chaine decimale (jamais un "
        + "nombre JSON, refuse en entree) : au plus 15 chiffres avant la virgule et pas plus de decimales que la "
        + "devise n'en autorise (2 pour MAD, EUR, USD, SAR ; 0 pour JPY ; 3 pour TND, KWD). Aucun arrondi : un "
        + "montant trop precis est refuse (400 validation-failed). En sortie, amount est a l'echelle de la devise "
        + "(1250.00, 1250, 1250.000).")
public record MoneyJson(
        @Schema(type = "string", example = "1250.00", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Montant decimal en chaine, point decimal, sans exposant")
        @JsonDeserialize(using = MoneyJson.StrictStringDeserializer.class)
        @NotNull @Size(max = 40) @Pattern(regexp = "\\d{1,15}(\\.\\d+)?") String amount,
        @Schema(example = "EUR", description = "Code ISO 4217 en majuscules")
        @ValidCurrencyCode String currency) {

    /** Forme JSON du montant, a l'echelle de la devise (sans arrondi). */
    public static MoneyJson from(Money money) {
        return new MoneyJson(money.toPlainAmount(), money.currency().getCurrencyCode());
    }

    /**
     * Montant strictement positif, dans la devise envoyee ou, si elle est absente, dans
     * {@code defaultCurrency}.
     *
     * @throws InvalidMoneyException si le montant ou la devise est refuse
     */
    public Money toPositiveMoney(String defaultCurrency) {
        return Money.exact(amount, currency != null ? currency : defaultCurrency).requirePositive();
    }

    /** N'accepte qu'une chaine JSON : un nombre, un booleen ou un objet rend le corps illisible. */
    static final class StrictStringDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                return parser.getString();
            }
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
    }
}
