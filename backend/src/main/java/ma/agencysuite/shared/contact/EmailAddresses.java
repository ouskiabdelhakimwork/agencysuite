package ma.agencysuite.shared.contact;

import java.util.Locale;

/** Adresses e-mail stockees sans espaces et en minuscules (comparaison des doublons, recherche). */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** Adresse normalisee, {@code null} si elle est absente ou vide. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
