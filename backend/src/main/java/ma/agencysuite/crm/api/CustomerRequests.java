package ma.agencysuite.crm.api;

import static ma.agencysuite.crm.api.Inputs.blankToNull;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import ma.agencysuite.crm.domain.ContactData;
import ma.agencysuite.crm.domain.CustomerData;
import ma.agencysuite.crm.domain.CustomerSource;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.shared.contact.ValidCountryCode;
import ma.agencysuite.shared.contact.ValidPhoneNumber;

/**
 * Corps des requetes de creation et de modification d'un client. Les drapeaux facultatifs sont des
 * {@link Boolean} (absents : faux) : Jackson 3 refuse un champ primitif absent.
 */
final class CustomerRequests {

    static final String ICE_PATTERN = "\\d{15}";
    static final String LOCALE_PATTERN = "fr|ar|en";

    private CustomerRequests() {
    }

    /** POST /api/v1/customers. ICE, IF et RC sont refuses pour un particulier (400). */
    record CreateCustomerRequest(
            @NotNull CustomerType type,
            @NotBlank @Size(max = 200) String name,
            @Schema(description = "ICE : exactement 15 chiffres (format a confirmer, RG-19) ; entreprise seulement")
            @Pattern(regexp = ICE_PATTERN) String ice,
            @Size(max = 20) String ifNumber,
            @Size(max = 50) String rc,
            @Schema(description = "Stocke en minuscules") @Email @Size(max = 254) String email,
            @Schema(description = "Numero marocain ou international ; stocke en E.164 (0612345678 -> +212612345678)")
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @Schema(description = "Code ISO 3166-1 alpha-2 en majuscules, MA par defaut") @ValidCountryCode String country,
            @Schema(description = "fr (defaut), ar ou en") @Pattern(regexp = LOCALE_PATTERN) String preferredLocale,
            @Size(max = 100) String sector,
            CustomerSource source,
            @Schema(description = "Utilisateur responsable, de la meme agence") UUID ownerUserId,
            @Size(max = 2000) String notes,
            @Schema(description = "Vrai pour creer malgre un doublon probable (409 possible-duplicate)")
            Boolean confirmDuplicate) {

        CreateCustomerRequest {
            ice = blankToNull(ice);
            ifNumber = blankToNull(ifNumber);
            rc = blankToNull(rc);
            email = blankToNull(email);
            phone = blankToNull(phone);
            country = blankToNull(country);
            preferredLocale = blankToNull(preferredLocale);
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        /** Un particulier n'a ni ICE, ni IF, ni RC. */
        @AssertTrue
        @Schema(hidden = true)
        boolean isLegalIdentifiersOnlyForCompany() {
            return type != CustomerType.INDIVIDUAL || (ice == null && ifNumber == null && rc == null);
        }

        CustomerData toData() {
            return new CustomerData(type, name, ice, ifNumber, rc, email, phone, addressLine, city, country,
                    preferredLocale, sector, source, ownerUserId, notes);
        }
    }

    /**
     * PUT /api/v1/customers/{id} : remplace tous les champs modifiables. Passer en particulier
     * efface ICE, IF et RC (refuse si l'entreprise a des contacts).
     */
    record UpdateCustomerRequest(
            @Schema(description = "Version de la fiche lue (verrouillage optimiste)") @NotNull Long version,
            @NotNull CustomerType type,
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = ICE_PATTERN) String ice,
            @Size(max = 20) String ifNumber,
            @Size(max = 50) String rc,
            @Email @Size(max = 254) String email,
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @ValidCountryCode String country,
            @Pattern(regexp = LOCALE_PATTERN) String preferredLocale,
            @Size(max = 100) String sector,
            CustomerSource source,
            UUID ownerUserId,
            @Size(max = 2000) String notes,
            Boolean confirmDuplicate) {

        UpdateCustomerRequest {
            ice = blankToNull(ice);
            ifNumber = blankToNull(ifNumber);
            rc = blankToNull(rc);
            email = blankToNull(email);
            phone = blankToNull(phone);
            country = blankToNull(country);
            preferredLocale = blankToNull(preferredLocale);
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        CustomerData toData() {
            return new CustomerData(type, name, ice, ifNumber, rc, email, phone, addressLine, city, country,
                    preferredLocale, sector, source, ownerUserId, notes);
        }
    }

    /** POST /api/v1/customers/{id}/contacts. */
    record ContactRequest(
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 100) String jobTitle,
            @Email @Size(max = 254) String email,
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Schema(description = "Contact principal : retire ce statut a l'ancien") Boolean isPrimary) {

        ContactRequest {
            email = blankToNull(email);
            phone = blankToNull(phone);
            isPrimary = Boolean.TRUE.equals(isPrimary);
        }

        ContactData toData() {
            return new ContactData(fullName, jobTitle, email, phone, isPrimary);
        }
    }

    /** PUT /api/v1/customers/{id}/contacts/{contactId} : remplace les champs ; version obligatoire. */
    record UpdateContactRequest(
            @Schema(description = "Version du contact lu (verrouillage optimiste)") @NotNull Long version,
            @NotBlank @Size(max = 200) String fullName,
            @Size(max = 100) String jobTitle,
            @Email @Size(max = 254) String email,
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Schema(description = "Contact principal : retire ce statut a l'ancien") Boolean isPrimary) {

        UpdateContactRequest {
            email = blankToNull(email);
            phone = blankToNull(phone);
            isPrimary = Boolean.TRUE.equals(isPrimary);
        }

        ContactData toData() {
            return new ContactData(fullName, jobTitle, email, phone, isPrimary);
        }
    }
}
