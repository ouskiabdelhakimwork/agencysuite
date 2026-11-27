package ma.agencysuite.crm.application;

/** Une entreprise qui a des contacts ne peut pas devenir un particulier : 409 customer-has-contacts. */
public class CustomerHasContactsException extends CrmException {

    public CustomerHasContactsException() {
        super("Le client a des contacts : passage en particulier refuse");
    }
}
