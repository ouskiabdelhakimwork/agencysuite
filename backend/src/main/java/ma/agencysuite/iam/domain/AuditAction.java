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
    USER_LOGGED_IN,
    /** Organization Keycloak de l'agence creee (premiere invitation ou rattachement du gerant). */
    ORGANIZATION_PROVISIONED,
    USER_INVITED,
    /** Premiere connexion d'un utilisateur invite : INVITED &rarr; ACTIVE. */
    USER_ACTIVATED,
    INVITATION_RESENT,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    USER_DISABLED,
    USER_ENABLED,
    /** Client archive (module crm, via AuditableEvent). */
    CUSTOMER_ARCHIVED,
    /** Client restaure (module crm, via AuditableEvent). */
    CUSTOMER_RESTORED
}
