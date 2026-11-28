package ma.agencysuite.supplier.domain;

/**
 * Champs modifiables d'un fournisseur, tels que saisis (deja valides par l'API). La normalisation
 * (espaces, e-mail en minuscules, telephone E.164, valeurs par defaut) est faite par {@link Supplier}.
 */
public record SupplierData(SupplierType type, String name, String ice, String email, String phone,
        String contactName, String addressLine, String city, String country, String defaultCurrency,
        String paymentTerms, String notes) {
}
