package ma.agencysuite.platform.application;

import ma.agencysuite.platform.domain.SubdomainViolation;

/** Sous-domaine invalide ou reserve : 400, type {@code invalid-subdomain} (+ propriete {@code reason}). */
public class InvalidSubdomainException extends PlatformException {

    private final SubdomainViolation reason;

    public InvalidSubdomainException(SubdomainViolation reason) {
        super("Sous-domaine refuse : " + reason);
        this.reason = reason;
    }

    public SubdomainViolation getReason() {
        return reason;
    }
}
