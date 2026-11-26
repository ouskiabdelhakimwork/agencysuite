package ma.agencysuite.iam.domain;

/**
 * Roles systeme attribuables aux utilisateurs d'une agence. Alignes exactement sur la table
 * {@code system_role} (migration V4, verifie par ReferenceDataTest) ; {@code user_role.role_code}
 * y fait reference.
 */
public enum SystemRole {
    /** Gerant de l'agence : toutes les permissions. Une agence en garde toujours au moins un. */
    MANAGER,
    SALES,
    OPERATIONS,
    ACCOUNTANT
}
