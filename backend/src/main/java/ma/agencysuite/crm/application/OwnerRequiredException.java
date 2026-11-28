package ma.agencysuite.crm.application;

/**
 * Aucun responsable donne et pas d'utilisateur d'agence courant pour le choisir par defaut (membre
 * de l'editeur agissant dans une agence, sans compte user_account) : 400 validation-failed.
 */
public class OwnerRequiredException extends CrmException {

    public OwnerRequiredException() {
        super("Responsable obligatoire : aucun utilisateur d'agence courant");
    }
}
