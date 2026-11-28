package ma.agencysuite.crm.domain;

/** Comment la demande est arrivee a l'agence (valeurs du modele de donnees). */
public enum RequestChannel {
    /** Formulaire (saisi par l'agence a ce lot ; formulaire web public hors perimetre). */
    FORM,
    EMAIL,
    WHATSAPP,
    PHONE,
    /** Visite a l'agence. */
    WALK_IN
}
