package ma.agencysuite.crm.application;

/**
 * Erreur metier du module CRM, traduite en reponse problem+json. Le message (pour les journaux)
 * ne contient aucune donnee personnelle et n'est jamais renvoye au client.
 */
public abstract class CrmException extends RuntimeException {

    protected CrmException(String message) {
        super(message);
    }
}
