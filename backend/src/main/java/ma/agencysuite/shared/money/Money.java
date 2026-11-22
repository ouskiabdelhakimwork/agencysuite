package ma.agencysuite.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Montant exact dans une devise (ADR-15) : jamais de double ni de float.
 *
 * <p>Le montant est stocke avec 4 decimales, comme la colonne NUMERIC(19,4).
 * L'arrondi a l'unite monetaire (2 decimales pour le MAD) se fait uniquement a
 * l'affichage ou a l'emission d'un document, via {@link #roundedToMinorUnit()}.
 *
 * <p>Regle d'arrondi retenue : HALF_UP (arrondi commercial). A faire confirmer
 * par l'expert-comptable pour la TVA et les factures (RG-03).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final Currency MAD = Currency.getInstance("MAD");

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
