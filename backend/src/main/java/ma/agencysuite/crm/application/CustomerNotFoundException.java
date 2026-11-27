package ma.agencysuite.crm.application;

import java.util.UUID;

/** Client inconnu dans l'agence courante (y compris un client d'une autre agence) : 404 not-found. */
public class CustomerNotFoundException extends CrmException {

    public CustomerNotFoundException(UUID id) {
        super("Client introuvable : " + id);
    }
}
