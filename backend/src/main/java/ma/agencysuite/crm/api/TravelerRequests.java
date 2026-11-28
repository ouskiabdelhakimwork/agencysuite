package ma.agencysuite.crm.api;

import static ma.agencysuite.crm.api.Inputs.blankToNull;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.crm.domain.Gender;
import ma.agencysuite.crm.domain.TravelerData;
import ma.agencysuite.shared.contact.ValidCountryCode;
import ma.agencysuite.shared.contact.ValidPhoneNumber;

/**
 * Corps des requetes de creation et de modification d'un voyageur. Les drapeaux facultatifs sont des
 * {@link Boolean} (absents : faux) : Jackson 3 refuse un champ primitif absent.
 */
final class TravelerRequests {

    /** La date de naissance doit etre posterieure a cette date (contrainte aussi en base). */
    static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    static final String BIRTH_DATE_RULE = "Date de naissance (AAAA-MM-JJ), ni future ni anterieure au 02/01/1900. "
            + "Reservee a la permission identity.read : sans elle, une date non nulle donne 403 permission-denied, "
            + "et une date absente ou nulle ne modifie pas la date enregistree. Avec elle, la date envoyee "
            + "remplace l'ancienne (nulle : effacee).";

    static final String BIRTH_PLACE_RULE = "Lieu de naissance. Meme regle que birthDate (permission identity.read) : "
            + "sans elle, un lieu non vide donne 403 permission-denied, et un lieu absent ou vide ne modifie pas le "
            + "lieu enregistre. Avec elle, le lieu envoye remplace l'ancien (absent ou vide : efface).";

    private TravelerRequests() {
    }

    /** POST /api/v1/travelers. */
    record CreateTravelerRequest(
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 100) String firstName,
            @Schema(description = "Nom en lettres arabes") @Size(max = 100) String lastNameAr,
            @Schema(description = "Prenom en lettres arabes") @Size(max = 100) String firstNameAr,
            Gender gender,
            @Schema(description = BIRTH_DATE_RULE) @PastOrPresent LocalDate birthDate,
            @Schema(description = BIRTH_PLACE_RULE) @Size(max = 100) String birthPlace,
            @Schema(description = "Code ISO 3166-1 alpha-2 en majuscules (MA, FR...)") @ValidCountryCode
            String nationality,
            @Schema(description = "Stocke en minuscules") @Email @Size(max = 254) String email,
            @Schema(description = "Numero marocain ou international ; stocke en E.164 (0612345678 -> +212612345678)")
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Schema(description = "Regime alimentaire, texte libre (vegetarien, sans gluten...)") @Size(max = 200)
            String dietary,
            @Schema(description = "Personne a prevenir") @Size(max = 200) String emergencyContactName,
            @Schema(description = "Telephone de la personne a prevenir ; stocke en E.164") @ValidPhoneNumber
            @Size(max = 30) String emergencyContactPhone,
            @Size(max = 2000) String notes,
            @Schema(description = "Clients de l'agence auxquels rattacher le voyageur (non archives)")
            @Size(max = 100) List<@NotNull UUID> customerIds,
            @Schema(description = "Vrai pour creer malgre un doublon probable (409 possible-duplicate)")
            Boolean confirmDuplicate) {

        CreateTravelerRequest {
            lastNameAr = blankToNull(lastNameAr);
            birthPlace = blankToNull(birthPlace);
            firstNameAr = blankToNull(firstNameAr);
            nationality = blankToNull(nationality);
            email = blankToNull(email);
            phone = blankToNull(phone);
            emergencyContactPhone = blankToNull(emergencyContactPhone);
            customerIds = customerIds == null ? List.of() : customerIds;
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        @AssertTrue
        @Schema(hidden = true)
        boolean isBirthDateAfterMinimum() {
            return birthDate == null || birthDate.isAfter(MIN_BIRTH_DATE);
        }

        TravelerData toData() {
            return new TravelerData(lastName, firstName, lastNameAr, firstNameAr, gender, birthDate, birthPlace,
                    nationality, email, phone, dietary, emergencyContactName, emergencyContactPhone, notes);
        }

        Set<UUID> customerIdSet() {
            return new LinkedHashSet<>(customerIds);
        }
    }

    /** PUT /api/v1/travelers/{id} : remplace les champs modifiables ; ne touche pas aux liens. */
    record UpdateTravelerRequest(
            @Schema(description = "Version de la fiche lue (verrouillage optimiste)") @NotNull Long version,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastNameAr,
            @Size(max = 100) String firstNameAr,
            Gender gender,
            @Schema(description = BIRTH_DATE_RULE) @PastOrPresent LocalDate birthDate,
            @Schema(description = BIRTH_PLACE_RULE) @Size(max = 100) String birthPlace,
            @ValidCountryCode String nationality,
            @Email @Size(max = 254) String email,
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Size(max = 200) String dietary,
            @Size(max = 200) String emergencyContactName,
            @ValidPhoneNumber @Size(max = 30) String emergencyContactPhone,
            @Size(max = 2000) String notes,
            Boolean confirmDuplicate) {

        UpdateTravelerRequest {
            lastNameAr = blankToNull(lastNameAr);
            birthPlace = blankToNull(birthPlace);
            firstNameAr = blankToNull(firstNameAr);
            nationality = blankToNull(nationality);
            email = blankToNull(email);
            phone = blankToNull(phone);
            emergencyContactPhone = blankToNull(emergencyContactPhone);
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        @AssertTrue
        @Schema(hidden = true)
        boolean isBirthDateAfterMinimum() {
            return birthDate == null || birthDate.isAfter(MIN_BIRTH_DATE);
        }

        TravelerData toData() {
            return new TravelerData(lastName, firstName, lastNameAr, firstNameAr, gender, birthDate, birthPlace,
                    nationality, email, phone, dietary, emergencyContactName, emergencyContactPhone, notes);
        }
    }
}
