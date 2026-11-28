package ma.agencysuite.supplier.domain;

/** Petites regles de saisie communes aux entites du module. */
final class Texts {

    private Texts() {
    }

    /** Texte sans espaces de debut et de fin, {@code null} s'il est absent ou vide. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Texte obligatoire, sans espaces de debut et de fin. */
    static String required(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " obligatoire");
        }
        return trimmed;
    }
}
