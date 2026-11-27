package ma.agencysuite.crm.application;

import java.util.UUID;

/** Contact inconnu pour ce client (autre client, autre agence ou inexistant) : 404 not-found. */
public class ContactNotFoundException extends CrmException {

    public ContactNotFoundException(UUID id) {
        super("Contact introuvable : " + id);
    }
}
