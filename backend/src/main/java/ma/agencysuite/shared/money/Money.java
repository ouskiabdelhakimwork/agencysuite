package ma.agencysuite.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Montant exact dans une devise (ADR-15) : jamais de double ni de float.
 *
 * <p>Le montant est stocke avec 4 decimales, comme la colonne NUMERIC(19,4).
 * L'arrondi a l'unite monetaire (2 decimales pour le MAD) se fait uniquement a
 * l'affichage ou a l'emission d'un document, via {@link #roundedToMinorUnit()}.
 *
 * <p>Regle d'arrondi retenue : HALF_UP (arrondi commercial). A faire confirmer
 * par l'expert-comptable pour la TVA et les factures (RG-03).
 *
 * <p>Montants saisis (etape 5, lot 3) : {@link #exact(String, String)} refuse au lieu d'arrondir
 * (trop de decimales pour la devise, devise inconnue) ; la sortie JSON passe par
 * {@link #toPlainAmount()} et {@link MoneyJson} (montant en chaine).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final Currency MAD = Currency.getInstance("MAD");
    /** Chiffres avant la virgule autorises par NUMERIC(19,4). */
    public static final int MAX_INTEGER_DIGITS = 15;

    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money mad(String amount) {
        return new Money(new BigDecimal(amount), MAD);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Montant saisi, accepte tel quel <b>sans aucun arrondi</b> (etape 5, lot 3) : chaine decimale
     * simple ({@code 1250}, {@code 1250.5}, {@code -3.20} ; ni exposant, ni espace, ni separateur de
     * milliers), au plus {@value #MAX_INTEGER_DIGITS} chiffres avant la virgule (NUMERIC(19,4)) et
     * pas plus de decimales significatives que la devise n'en autorise
     * ({@link Currency#getDefaultFractionDigits()} : 2 pour MAD, EUR, USD ; 0 pour JPY ; 3 pour TND).
     * Les zeros finaux ne comptent pas ({@code 95.500} EUR est accepte : aucune valeur n'est perdue).
     *
     * @param currencyCode code ISO 4217 en majuscules, connu de {@link Currency}
     * @throws InvalidMoneyException si la devise ou le montant ne respecte pas ces regles
     */
    public static Money exact(String amount, String currencyCode) {
        Currency currency = currencyOf(currencyCode);
        if (amount == null || !DECIMAL.matcher(amount).matches()) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.MALFORMED_AMOUNT);
        }
        return exact(new BigDecimal(amount), currency);
    }

    /** Meme regle que {@link #exact(String, String)} pour un montant deja decimal. */
    public static Money exact(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        }
        BigDecimal significant = amount.stripTrailingZeros();
        if (Math.max(significant.scale(), 0) > digits) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.TOO_MANY_DECIMALS);
        }
        if (significant.precision() - significant.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.TOO_LARGE);
        }
        // Le constructeur porte l'echelle a 4 : sans effet sur la valeur, la devise en autorise au plus 4.
        return new Money(amount, currency);
    }

    /**
     * Devise ISO 4217 connue de {@link Currency}, en majuscules, avec un nombre de decimales defini
     * (les pseudo-devises comme XAU ou XXX sont refusees).
     *
     * @throws InvalidMoneyException si le code est absent, inconnu ou pas en majuscules
     */
    public static Currency currencyOf(String currencyCode) {
        if (currencyCode == null || !CURRENCY_CODE.matcher(currencyCode).matches()) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        }
        try {
            Currency currency = Currency.getInstance(currencyCode);
            if (currency.getDefaultFractionDigits() < 0) {
                throw new InvalidMoneyException(InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
            }
            return currency;
        } catch (IllegalArgumentException e) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.UNKNOWN_CURRENCY);
        }
    }

    /** Vrai si le code est une devise acceptee par {@link #currencyOf(String)}. */
    public static boolean isKnownCurrency(String currencyCode) {
        try {
            currencyOf(currencyCode);
            return true;
        } catch (InvalidMoneyException e) {
            return false;
        }
    }

    /**
     * Ce montant s'il est strictement positif.
     *
     * @throws InvalidMoneyException sinon (montant nul ou negatif)
     */
    public Money requirePositive() {
        if (amount.signum() <= 0) {
            throw new InvalidMoneyException(InvalidMoneyException.Reason.NOT_POSITIVE);
        }
        return this;
    }

    /**
     * Montant en texte, a l'echelle de la devise ({@code 1250.00} EUR, {@code 1250} JPY,
     * {@code 1250.000} TND), <b>sans arrondi</b> : si le montant a plus de decimales significatives
     * que la devise (resultat d'un calcul), elles sont toutes gardees.
     */
    public String toPlainAmount() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        int scale = Math.max(digits, amount.stripTrailingZeros().scale());
        return amount.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(amount.multiply(factor), currency);
    }

    public Money times(long quantity) {
        return times(BigDecimal.valueOf(quantity));
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /** Montant arrondi a l'unite monetaire de la devise (ex. 2 decimales pour MAD, EUR, USD). */
    public BigDecimal roundedToMinorUnit() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        return amount.setScale(digits, ROUNDING);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Devises differentes : " + currency.getCurrencyCode() + " et " + other.currency.getCurrencyCode()
                            + ". Convertir explicitement avec un taux fige (ADR-31).");
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
