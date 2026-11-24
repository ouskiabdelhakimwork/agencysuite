package ma.agencysuite.iam.domain;

/** Etat du compte d'un membre de l'editeur. Seul ACTIVE donne acces au back-office. */
public enum PlatformUserStatus {
    ACTIVE,
    DISABLED
}
