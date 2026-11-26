package ma.agencysuite.iam.application;

/**
 * L'administration du fournisseur d'identite (Keycloak) est injoignable, non configuree ou a
 * repondu une erreur inattendue : 503 identity-admin-unavailable.
 *
 * <p>Le message ne nomme que l'operation et le statut HTTP : jamais le secret, le jeton, l'e-mail
 * ni la reponse brute du fournisseur. Aucune cause n'est chainee pour la meme raison.
 */
public class IdentityAdminUnavailableException extends RuntimeException {

    public IdentityAdminUnavailableException(String message) {
        super(message);
    }
}
