package ma.agencysuite.shared.module;

/**
 * Modules metier activables et factures separement (01-cahier-des-charges.md, section 2.3).
 * Place dans le noyau partage car tous les modules s'en servent pour declarer
 * le module dont ils dependent (@RequiresModule, lot 3).
 *
 * <p>Les codes correspondent a la table {@code module} (migration V2).
 */
public enum ModuleCode {
    TRAVEL,
    TICKETING,
    GIFTBOX,
    TRANSPORT,
    VISA,
    MICE,
    PORTAL,
    OMRA_HAJJ,
    INBOUND,
    FLEET
}
