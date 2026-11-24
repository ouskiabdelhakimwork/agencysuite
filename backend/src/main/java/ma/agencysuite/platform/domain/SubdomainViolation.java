package ma.agencysuite.platform.domain;

/** Regle non respectee par un sous-domaine d'agence. */
public enum SubdomainViolation {
    /** Pas 3 a 63 caracteres parmi minuscules, chiffres et tirets, ou tiret en debut/fin. */
    INVALID_FORMAT,
    /** Nom reserve a la plateforme (www, api, admin...). */
    RESERVED
}
