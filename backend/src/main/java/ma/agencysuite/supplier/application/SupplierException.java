package ma.agencysuite.supplier.application;

/**
 * Erreur metier du module supplier, traduite en reponse problem+json. Le message (pour les
 * journaux) ne contient aucune donnee personnelle et n'est jamais renvoye au client.
 */
public abstract class SupplierException extends RuntimeException {

    protected SupplierException(String message) {
        super(message);
    }

    /** Fournisseur, prestation ou tarif inconnu dans l'agence courante (y compris d'une autre agence) : 404. */
    public static class NotFound extends SupplierException {

        public NotFound(String what, java.util.UUID id) {
            super(what + " introuvable : " + id);
        }
    }

    /** Creation d'une prestation pour un fournisseur archive : 409 supplier-archived. */
    public static class SupplierArchived extends SupplierException {

        public SupplierArchived() {
            super("Fournisseur archive : prestation refusee");
        }
    }

    /** Creation ou modification d'un tarif d'une prestation archivee : 409 product-archived. */
    public static class ProductArchived extends SupplierException {

        public ProductArchived() {
            super("Prestation archivee : tarif refuse");
        }
    }
}
