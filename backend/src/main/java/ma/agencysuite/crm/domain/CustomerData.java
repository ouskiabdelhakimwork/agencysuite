package ma.agencysuite.crm.domain;

import java.util.UUID;

/**
 * Champs modifiables d'un client, tels que saisis (deja valides par l'API). La normalisation
 * (espaces, e-mail en minuscules, telephone E.164, valeurs par defaut) est faite par
 * {@link Customer}.
 */
public record CustomerData(CustomerType type, String name, String ice, String ifNumber, String rc, String email,
        String phone, String addressLine, String city, String country, String preferredLocale, String sector,
        CustomerSource source, UUID ownerUserId, String notes) {
}
