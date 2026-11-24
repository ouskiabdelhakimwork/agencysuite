package ma.agencysuite.iam.domain;

/** Etat du compte d'un utilisateur d'agence. Seul ACTIVE donne acces a l'application. */
public enum UserStatus {
    /** Invitation envoyee, compte pas encore active (lot 5). */
    INVITED,
    ACTIVE,
    /** Compte desactive par l'agence : plus aucun acces, donnees conservees. */
    DISABLED
}
