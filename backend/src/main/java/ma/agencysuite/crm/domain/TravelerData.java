package ma.agencysuite.crm.domain;

import java.time.LocalDate;

/**
 * Champs modifiables d'un voyageur, tels que saisis (deja valides par l'API). La normalisation
 * (espaces, e-mail en minuscules, telephones E.164, {@code name_key}) est faite par
 * {@link Traveler}. La regle de la date et du lieu de naissance (permission {@code identity.read})
 * est une regle du cas d'usage.
 */
public record TravelerData(String lastName, String firstName, String lastNameAr, String firstNameAr, Gender gender,
        LocalDate birthDate, String birthPlace, String nationality, String email, String phone, String dietary,
        String emergencyContactName, String emergencyContactPhone, String notes) {
}
