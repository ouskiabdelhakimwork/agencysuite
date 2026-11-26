package ma.agencysuite.iam.application;

import java.util.UUID;

/** Agence close : aucun utilisateur ne peut y etre ajoute (409 agency-closed). */
public class AgencyClosedException extends RuntimeException {

    public AgencyClosedException(UUID agencyId) {
        super("Agence close : " + agencyId);
    }
}
