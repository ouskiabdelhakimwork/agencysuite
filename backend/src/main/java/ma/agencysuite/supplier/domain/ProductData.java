package ma.agencysuite.supplier.domain;

import ma.agencysuite.shared.i18n.LocalizedText;

/** Champs modifiables d'une prestation (deja valides par l'API) ; le fournisseur ne change jamais. */
public record ProductData(ProductType type, LocalizedText name, LocalizedText description,
        String cancellationPolicy) {
}
