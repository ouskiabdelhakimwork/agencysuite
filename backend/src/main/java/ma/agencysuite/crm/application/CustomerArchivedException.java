package ma.agencysuite.crm.application;

/** Rattachement d'un voyageur a un client archive refuse : 409 customer-archived. */
public class CustomerArchivedException extends CrmException {

    public CustomerArchivedException() {
        super("Client archive : rattachement refuse");
    }
}
