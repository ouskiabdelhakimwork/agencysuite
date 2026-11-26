package ma.agencysuite.iam.application;

/** Un utilisateur ne peut pas se desactiver lui-meme : 409 cannot-disable-self. */
public class CannotDisableSelfException extends RuntimeException {

    public CannotDisableSelfException() {
        super("Desactivation de soi-meme refusee");
    }
}
