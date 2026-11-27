package ma.agencysuite.shared.contact;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Optional;

/**
 * Numeros de telephone : analyse avec libphonenumber (Google), region par defaut le Maroc, et
 * stockage au format international E.164 ({@code 0612345678} &rarr; {@code +212612345678}).
 * Reutilisable par tous les modules (clients, voyageurs, fournisseurs).
 *
 * <p>Jamais de numero dans les journaux : les messages d'erreur ne le reprennent pas.
 */
public final class PhoneNumbers {

    /** Region utilisee pour un numero saisi sans indicatif international. */
    public static final String DEFAULT_REGION = "MA";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /** Forme E.164 d'un numero valide, vide si le numero est absent, illisible ou invalide. */
    public static Optional<String> toE164(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            PhoneNumber number = UTIL.parse(raw.strip(), DEFAULT_REGION);
            return UTIL.isValidNumber(number)
                    ? Optional.of(UTIL.format(number, PhoneNumberFormat.E164))
                    : Optional.empty();
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    /** Vrai si le numero est valide (une valeur absente est valide : le champ est facultatif). */
    public static boolean isValidOrBlank(String raw) {
        return raw == null || raw.isBlank() || toE164(raw).isPresent();
    }

    /**
     * Forme E.164 d'un numero facultatif ; {@code null} s'il est absent.
     *
     * @throws IllegalArgumentException si le numero est invalide (la validation d'entree doit l'avoir refuse)
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return toE164(raw).orElseThrow(() -> new IllegalArgumentException("Numero de telephone invalide"));
    }
}
