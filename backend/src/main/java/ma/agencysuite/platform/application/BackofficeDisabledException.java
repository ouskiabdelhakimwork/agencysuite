package ma.agencysuite.platform.application;

/** Back-office appele alors qu'il est desactive : 403, type {@code backoffice-disabled}. */
public class BackofficeDisabledException extends PlatformException {

    public BackofficeDisabledException() {
        super("Back-office desactive (agencysuite.backoffice.unsecured-access=false)");
    }
}
