package ma.agencysuite.iam.application;

/**
 * Aucun utilisateur courant (UserContext absent) la ou il en faut un : 403 access-denied. Ne
 * devrait pas arriver derriere la chaine de securite des agences, qui refuse avant.
 */
public class UserRequiredException extends RuntimeException {

    public UserRequiredException() {
        super("Utilisateur courant requis");
    }
}
