package ma.agencysuite.iam.application;

import java.util.UUID;

/** Agence inconnue (rattachement du gerant par le back-office) : 404 not-found. */
public class UnknownAgencyException extends RuntimeException {

    public UnknownAgencyException(UUID agencyId) {
        super("Agence introuvable : " + agencyId);
    }
}
