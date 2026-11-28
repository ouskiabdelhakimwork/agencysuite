package ma.agencysuite.supplier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;
import ma.agencysuite.shared.text.SearchKeys;

/**
 * Fournisseur de l'agence (US-601) : hotel, riad, transporteur, DMC, lieu, guide...
 *
 * <p>Regles portees par l'entite : espaces retires ; e-mail en minuscules ; telephone en E.164 ;
 * pays MA et devise MAD par defaut ; {@code name_key} recalculee a chaque ecriture. L'ICE est
 * facultatif quel que soit le type. Changer la devise par defaut ne modifie aucun tarif existant
 * (chaque tarif garde sa devise). Pas de suppression : archivage et restauration.
 */
@Entity
@Table(name = "supplier")
public class Supplier extends AuthoredAgencyEntity {

    public static final String DEFAULT_COUNTRY = "MA";
    public static final String DEFAULT_CURRENCY = "MAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupplierType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Column(length = 15)
    private String ice;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "payment_terms", length = 500)
    private String paymentTerms;

    @Column(length = 2000)
    private String notes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Supplier() {
        // pour JPA
    }

    public static Supplier create(SupplierData data) {
        Supplier supplier = new Supplier();
        supplier.update(data);
        return supplier;
    }

    /** Remplace les champs modifiables. */
    public void update(SupplierData data) {
        this.type = Objects.requireNonNull(data.type(), "type");
        this.name = Texts.required(data.name(), "name");
        this.nameKey = SearchKeys.normalize(name);
        this.ice = Texts.trimToNull(data.ice());
        this.email = EmailAddresses.normalize(data.email());
        this.phone = PhoneNumbers.normalize(data.phone());
        this.contactName = Texts.trimToNull(data.contactName());
        this.addressLine = Texts.trimToNull(data.addressLine());
        this.city = Texts.trimToNull(data.city());
        String countryCode = Texts.trimToNull(data.country());
        this.country = countryCode == null ? DEFAULT_COUNTRY : countryCode;
        String currency = Texts.trimToNull(data.defaultCurrency());
        this.defaultCurrency = currency == null ? DEFAULT_CURRENCY : currency;
        this.paymentTerms = Texts.trimToNull(data.paymentTerms());
        this.notes = Texts.trimToNull(data.notes());
    }

    /** @return vrai si le fournisseur vient d'etre archive (faux s'il l'etait deja) */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** @return vrai si le fournisseur vient d'etre restaure (faux s'il ne l'etait pas) */
    public boolean restore() {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        return true;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public SupplierType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    /** Cle technique de recherche ; jamais renvoyee par l'API. */
    public String getNameKey() {
        return nameKey;
    }

    public String getIce() {
        return ice;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getContactName() {
        return contactName;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
