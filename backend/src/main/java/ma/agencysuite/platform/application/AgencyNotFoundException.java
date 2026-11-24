package ma.agencysuite.platform.application;

import java.util.UUID;

public class AgencyNotFoundException extends ResourceNotFoundException {

    public AgencyNotFoundException(UUID agencyId) {
        super("Agence introuvable : " + agencyId);
    }
}
