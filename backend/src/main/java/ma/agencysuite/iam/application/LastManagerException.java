package ma.agencysuite.iam.application;

/** L'operation retirerait le dernier gerant actif ou invite de l'agence : 409 last-manager. */
public class LastManagerException extends RuntimeException {

    public LastManagerException() {
        super("Dernier gerant de l'agence");
    }
}
