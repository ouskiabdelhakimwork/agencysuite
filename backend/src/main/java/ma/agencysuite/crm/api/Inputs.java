package ma.agencysuite.crm.api;

/** Nettoyage des champs saisis, avant la validation : un champ facultatif vide vaut absent. */
final class Inputs {

    private Inputs() {
    }

    /** {@code null} si la valeur est absente ou ne contient que des espaces ; sinon sans espaces autour. */
    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
