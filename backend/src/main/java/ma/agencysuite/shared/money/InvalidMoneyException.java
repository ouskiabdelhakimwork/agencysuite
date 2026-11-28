package ma.agencysuite.shared.money;

import java.util.Objects;

/**
 * Montant saisi refuse par {@link Money#exact(String, String)} ou {@link Money#requirePositive()} :
 * 400 {@code validation-failed} (gestionnaire commun de shared), avec la raison en code technique.
 * Jamais d'arrondi a la place du refus (RG-03 non validee). Le message ne reprend pas le montant.
 */
public class InvalidMoneyException extends RuntimeException {

    /** Raison du refus, renvoyee dans la propriete {@code reason} du probleme. */
    public enum Reason {
        /** Code devise absent, inconnu de ISO 4217 (java.util.Currency) ou pas en majuscules. */
        UNKNOWN_CURRENCY,
        /** Montant absent ou qui n'est pas une chaine decimale simple. */
        MALFORMED_AMOUNT,
        /** Plus de decimales significatives que la devise n'en autorise. */
        TOO_MANY_DECIMALS,
        /** Plus de 15 chiffres avant la virgule (NUMERIC(19,4)). */
        TOO_LARGE,
        /** Montant nul ou negatif la ou un montant strictement positif est attendu. */
        NOT_POSITIVE
    }

    private final Reason reason;

    public InvalidMoneyException(Reason reason) {
        super("Montant refuse : " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
