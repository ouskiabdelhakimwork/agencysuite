package ma.agencysuite.supplier.domain;

/**
 * Base d'un tarif (unite a laquelle le prix s'applique), alignee sur la contrainte CHECK de V9.
 * L'ordre de declaration est l'ordre d'affichage des tarifs.
 */
public enum RateBasis {
    SINGLE,
    DOUBLE,
    TRIPLE,
    QUADRUPLE,
    PER_PERSON,
    PER_UNIT
}
