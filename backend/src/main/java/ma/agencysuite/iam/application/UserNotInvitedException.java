package ma.agencysuite.iam.application;

/** Renvoi d'invitation pour un utilisateur qui n'est pas (ou plus) INVITED : 409 user-not-invited. */
public class UserNotInvitedException extends RuntimeException {

    public UserNotInvitedException() {
        super("Utilisateur non invite");
    }
}
