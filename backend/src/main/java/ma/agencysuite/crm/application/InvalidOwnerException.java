package ma.agencysuite.crm.application;

/** Le responsable indique n'est pas un utilisateur de l'agence : 400 invalid-owner. */
public class InvalidOwnerException extends CrmException {

    public InvalidOwnerException() {
        super("Responsable inconnu dans l'agence");
    }
}
