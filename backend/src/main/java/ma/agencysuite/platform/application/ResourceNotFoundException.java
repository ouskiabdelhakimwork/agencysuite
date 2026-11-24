package ma.agencysuite.platform.application;

/** Ressource introuvable (agence, palier, module) : 404, type {@code not-found}. */
public abstract class ResourceNotFoundException extends PlatformException {

    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
