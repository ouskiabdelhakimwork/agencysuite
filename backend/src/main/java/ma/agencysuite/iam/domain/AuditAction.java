package ma.agencysuite.iam.domain;

/**
 * Actions tracees dans le journal d'audit. Stockees en VARCHAR(50) (colonne audit_log.action) :
 * ajouter une valeur ne demande pas de migration (pas de contrainte CHECK, la liste grandit a
 * chaque etape).
 */
public enum AuditAction {
    AGENCY_CREATED,
    MODULE_ENABLED,
    MODULE_DISABLED,
    USER_LOGGED_IN
}
