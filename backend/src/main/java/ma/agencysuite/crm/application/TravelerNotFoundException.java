package ma.agencysuite.crm.application;

import java.util.UUID;

/** Voyageur inconnu dans l'agence courante (y compris un voyageur d'une autre agence) : 404 not-found. */
public class TravelerNotFoundException extends CrmException {

    public TravelerNotFoundException(UUID id) {
        super("Voyageur introuvable : " + id);
    }
}
