package ma.agencysuite.platform.application;

/** Sous-domaine deja attribue a une autre agence : 409, type {@code subdomain-taken}. */
public class SubdomainAlreadyTakenException extends PlatformException {

    private final String subdomain;

    public SubdomainAlreadyTakenException(String subdomain) {
        super("Sous-domaine deja pris : " + subdomain);
        this.subdomain = subdomain;
    }

    public String getSubdomain() {
        return subdomain;
    }
}
