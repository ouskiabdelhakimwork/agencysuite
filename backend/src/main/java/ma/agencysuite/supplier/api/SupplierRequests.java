package ma.agencysuite.supplier.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ma.agencysuite.shared.contact.ValidCountryCode;
import ma.agencysuite.shared.contact.ValidPhoneNumber;
import ma.agencysuite.shared.i18n.LocalizedText;
import ma.agencysuite.shared.i18n.ValidLocalizedText;
import ma.agencysuite.shared.money.MoneyJson;
import ma.agencysuite.shared.money.ValidCurrencyCode;
import ma.agencysuite.supplier.application.RateBook.RateInput;
import ma.agencysuite.supplier.domain.Product;
import ma.agencysuite.supplier.domain.ProductData;
import ma.agencysuite.supplier.domain.ProductType;
import ma.agencysuite.supplier.domain.RateBasis;
import ma.agencysuite.supplier.domain.SupplierData;
import ma.agencysuite.supplier.domain.SupplierType;

/**
 * Corps des requetes du module (fournisseurs, prestations, tarifs). Les drapeaux facultatifs sont
 * des {@link Boolean} (absents : faux) : Jackson 3 refuse un champ primitif absent. Un champ
 * facultatif vide vaut absent.
 */
final class SupplierRequests {

    static final String ICE_PATTERN = "\\d{15}";

    private SupplierRequests() {
    }

    /** POST /api/v1/suppliers. */
    record CreateSupplierRequest(
            @NotNull SupplierType type,
            @NotBlank @Size(max = 200) String name,
            @Schema(description = "ICE : exactement 15 chiffres (format a confirmer, RG-19), quel que soit le type")
            @Pattern(regexp = ICE_PATTERN) String ice,
            @Schema(description = "Stocke en minuscules") @Email @Size(max = 254) String email,
            @Schema(description = "Numero marocain ou international ; stocke en E.164 (0524000000 -> +212524000000)")
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Size(max = 200) String contactName,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @Schema(description = "Code ISO 3166-1 alpha-2 en majuscules, MA par defaut") @ValidCountryCode String country,
            @Schema(description = "Devise par defaut des tarifs, ISO 4217 en majuscules, MAD par defaut")
            @ValidCurrencyCode String defaultCurrency,
            @Schema(description = "Conditions de paiement en texte libre") @Size(max = 500) String paymentTerms,
            @Size(max = 2000) String notes,
            @Schema(description = "Vrai pour creer malgre un doublon probable (409 possible-duplicate)")
            Boolean confirmDuplicate) {

        CreateSupplierRequest {
            ice = blankToNull(ice);
            email = blankToNull(email);
            phone = blankToNull(phone);
            country = blankToNull(country);
            defaultCurrency = blankToNull(defaultCurrency);
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        SupplierData toData() {
            return new SupplierData(type, name, ice, email, phone, contactName, addressLine, city, country,
                    defaultCurrency, paymentTerms, notes);
        }
    }

    /** PUT /api/v1/suppliers/{id} : remplace tous les champs modifiables ; version obligatoire. */
    record UpdateSupplierRequest(
            @Schema(description = "Version de la fiche lue (verrouillage optimiste)") @NotNull Long version,
            @NotNull SupplierType type,
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = ICE_PATTERN) String ice,
            @Email @Size(max = 254) String email,
            @ValidPhoneNumber @Size(max = 30) String phone,
            @Size(max = 200) String contactName,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @ValidCountryCode String country,
            @Schema(description = "Changer la devise par defaut ne modifie pas les tarifs existants")
            @ValidCurrencyCode String defaultCurrency,
            @Size(max = 500) String paymentTerms,
            @Size(max = 2000) String notes,
            Boolean confirmDuplicate) {

        UpdateSupplierRequest {
            ice = blankToNull(ice);
            email = blankToNull(email);
            phone = blankToNull(phone);
            country = blankToNull(country);
            defaultCurrency = blankToNull(defaultCurrency);
            confirmDuplicate = Boolean.TRUE.equals(confirmDuplicate);
        }

        SupplierData toData() {
            return new SupplierData(type, name, ice, email, phone, contactName, addressLine, city, country,
                    defaultCurrency, paymentTerms, notes);
        }
    }

    /** POST /api/v1/suppliers/{supplierId}/products. */
    record CreateProductRequest(
            @NotNull ProductType type,
            @Schema(description = "Nom par langue (fr, ar, en) : au moins une langue, 200 caracteres au plus chacune")
            @ValidLocalizedText(required = true, max = Product.NAME_MAX_LENGTH) LocalizedText name,
            @Schema(description = "Description par langue (fr, ar, en), facultative, 2000 caracteres au plus chacune")
            @ValidLocalizedText(max = Product.DESCRIPTION_MAX_LENGTH) LocalizedText description,
            @Schema(description = "Conditions d'annulation, texte interne") @Size(max = 2000) String cancellationPolicy) {

        ProductData toData() {
            return new ProductData(type, name, description, cancellationPolicy);
        }
    }

    /** PUT /api/v1/products/{id} : remplace les champs modifiables (pas le fournisseur) ; version obligatoire. */
    record UpdateProductRequest(
            @Schema(description = "Version de la prestation lue (verrouillage optimiste)") @NotNull Long version,
            @NotNull ProductType type,
            @ValidLocalizedText(required = true, max = Product.NAME_MAX_LENGTH) LocalizedText name,
            @ValidLocalizedText(max = Product.DESCRIPTION_MAX_LENGTH) LocalizedText description,
            @Size(max = 2000) String cancellationPolicy) {

        ProductData toData() {
            return new ProductData(type, name, description, cancellationPolicy);
        }
    }

    /** POST /api/v1/products/{id}/rates. */
    record CreateRateRequest(
            @NotNull RateBasis basis,
            @Schema(description = "Premier jour de validite (inclus) ; une date passee est acceptee") @NotNull
            LocalDate validFrom,
            @Schema(description = "Dernier jour de validite (inclus), au plus tot validFrom") @NotNull LocalDate validTo,
            @Schema(description = "Prix d'achat ; devise facultative, celle du fournisseur par defaut")
            @NotNull @Valid MoneyJson purchase,
            @Schema(description = "Libelle libre (Haute saison, Ramadan...)") @Size(max = 100) String label) {

        /** {@code validTo >= validFrom}. */
        @AssertTrue
        @Schema(hidden = true)
        boolean isPeriodOrdered() {
            return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
        }

        RateInput toInput() {
            return new RateInput(basis, validFrom, validTo, purchase, label);
        }
    }

    /** PUT /api/v1/products/{productId}/rates/{rateId} : remplace les champs ; version obligatoire. */
    record UpdateRateRequest(
            @Schema(description = "Version du tarif lu (verrouillage optimiste)") @NotNull Long version,
            @NotNull RateBasis basis,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate validTo,
            @Schema(description = "Prix d'achat ; devise facultative, celle du tarif par defaut")
            @NotNull @Valid MoneyJson purchase,
            @Size(max = 100) String label) {

        @AssertTrue
        @Schema(hidden = true)
        boolean isPeriodOrdered() {
            return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
        }

        RateInput toInput() {
            return new RateInput(basis, validFrom, validTo, purchase, label);
        }
    }

    /** {@code null} si la valeur est absente ou ne contient que des espaces ; sinon sans espaces autour. */
    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
