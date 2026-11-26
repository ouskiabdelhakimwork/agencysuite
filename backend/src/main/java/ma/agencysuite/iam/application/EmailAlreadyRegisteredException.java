package ma.agencysuite.iam.application;

/**
 * L'e-mail est deja celui d'un utilisateur (de l'agence, ou du fournisseur d'identite) : 409
 * email-already-registered. Le message ne contient jamais l'e-mail.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("E-mail deja enregistre");
    }
}
