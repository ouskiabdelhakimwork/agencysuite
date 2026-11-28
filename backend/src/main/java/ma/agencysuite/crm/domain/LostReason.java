package ma.agencysuite.crm.domain;

/**
 * Raison de la perte d'une demande (liste a valider avec l'agence pilote ; tout changement passe
 * par une migration, contrainte CHECK de customer_request.lost_reason).
 */
public enum LostReason {
    PRICE,
    COMPETITOR,
    /** Dates ou disponibilites. */
    DATES_UNAVAILABLE,
    PROJECT_CANCELLED,
    /** Pas de reponse du client. */
    NO_RESPONSE,
    /** Autre : commentaire conseille, pas obligatoire. */
    OTHER
}
