package ma.agencysuite.crm.application;

import java.util.UUID;

/** Demande inconnue dans l'agence courante (y compris une demande d'une autre agence) : 404 not-found. */
public class CustomerRequestNotFoundException extends CrmException {

    public CustomerRequestNotFoundException(UUID id) {
        super("Demande introuvable : " + id);
    }
}
