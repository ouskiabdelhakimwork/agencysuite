package ma.agencysuite.iam.application;

import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Administration du fournisseur d'identite (port ; ADR-08, regle 2). Aucun type Keycloak ici :
 * l'adaptateur est dans {@code iam.infrastructure.keycloak}.
 *
 * <p>Les identifiants d'utilisateur sont les {@code sub} des jetons. Toute erreur inattendue ou
 * fournisseur injoignable : {@link IdentityAdminUnavailableException}.
 */
public interface IdentityProviderAdmin {

    /**
     * Organization de l'agence (alias = UUID de l'agence), creee si elle n'existe pas encore.
     * Idempotent.
     *
     * @param name nom affiche, unique chez le fournisseur
     */
    ProvisionedOrganization ensureOrganization(AgencyId agencyId, String name);

    /**
     * Cree l'utilisateur (active, e-mail non verifie, sans mot de passe).
     *
     * @param locale langue de ses e-mails et ecrans de connexion ({@code fr}, {@code ar}, {@code en})
     * @return son {@code sub}
     * @throws EmailAlreadyRegisteredException un utilisateur du fournisseur a deja cet e-mail
     */
    String createUser(String email, String firstName, String lastName, String locale);

    /** Rend l'utilisateur membre de l'Organization (sans effet s'il l'est deja). */
    void addMember(String organizationId, String sub);

    /**
     * Envoie l'e-mail d'invitation : lien a duree limitee pour verifier l'adresse et choisir son
     * mot de passe. Aucun mot de passe n'est genere, affiche ni envoye.
     */
    void sendInvitation(String sub);

    void setEnabled(String sub, boolean enabled);

    /** Ferme toutes les sessions de l'utilisateur. */
    void logout(String sub);

    /** Supprime l'utilisateur (compensation d'une invitation echouee). Sans effet s'il n'existe plus. */
    void deleteUser(String sub);
}
