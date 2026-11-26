package ma.agencysuite.iam.domain;

/**
 * Etat du compte d'un utilisateur d'agence. INVITED et ACTIVE donnent acces a l'application
 * (etape 3, lot 5) ; DISABLED, jamais.
 */
public enum UserStatus {
    /** Invitation envoyee ; passe ACTIVE a la premiere connexion (POST /api/v1/me/login-events). */
    INVITED,
    ACTIVE,
    /** Compte desactive par l'agence : plus aucun acces, donnees conservees. */
    DISABLED
}
