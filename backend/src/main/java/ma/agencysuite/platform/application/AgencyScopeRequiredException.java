package ma.agencysuite.platform.application;

/**
 * Requete reservee aux agences executee sans perimetre agence (aucune agence courante) :
 * 403, type {@code agency-scope-required}.
 */
public class AgencyScopeRequiredException extends PlatformException {

    public AgencyScopeRequiredException() {
        super("Aucune agence courante pour une requete reservee aux agences");
    }
}
