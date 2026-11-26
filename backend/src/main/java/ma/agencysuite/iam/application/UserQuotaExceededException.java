package ma.agencysuite.iam.application;

/** Le palier de l'agence ne permet pas un utilisateur de plus : 409 user-quota-exceeded. */
public class UserQuotaExceededException extends RuntimeException {

    public UserQuotaExceededException() {
        super("Nombre maximal d'utilisateurs du palier atteint");
    }
}
