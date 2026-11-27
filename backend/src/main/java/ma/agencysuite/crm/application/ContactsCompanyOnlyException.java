package ma.agencysuite.crm.application;

/** Seule une entreprise a des contacts : 409 contacts-company-only. */
public class ContactsCompanyOnlyException extends CrmException {

    public ContactsCompanyOnlyException() {
        super("Contacts reserves aux clients entreprises");
    }
}
