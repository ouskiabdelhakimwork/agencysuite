package ma.agencysuite.platform.domain;

/** Cycle de vie d'une agence dans le SaaS. */
public enum AgencyStatus {
    /** Periode d'essai. */
    TRIAL,
    ACTIVE,
    /** Acces bloque (impaye, demande de l'agence) ; donnees conservees. */
    SUSPENDED,
    /** Cloture demandee : export complet en cours (ADR-33). */
    CLOSING,
    /** Donnees supprimees et cles detruites. */
    CLOSED
}
