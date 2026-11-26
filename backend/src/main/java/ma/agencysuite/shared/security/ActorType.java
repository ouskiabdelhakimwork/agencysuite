package ma.agencysuite.shared.security;

/** Origine d'une action tracee dans le journal d'audit. */
public enum ActorType {
    /** Utilisateur d'une agence (table user_account). */
    AGENCY_USER,
    /** Membre de l'equipe de l'editeur, back-office (table platform_user). */
    PLATFORM_USER,
    /** Traitement sans utilisateur : tache planifiee, migration, initialisation. */
    SYSTEM
}
