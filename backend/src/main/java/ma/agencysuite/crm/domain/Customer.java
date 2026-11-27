package ma.agencysuite.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;

/**
 * Client de l'agence, particulier ou entreprise (US-401).
 *
 * <p>Regles portees par l'entite : ICE, IF et RC seulement pour une entreprise (un particulier
 * n'en a jamais, aussi verifie en base) ; e-mail en minuscules ; telephone en E.164 ; pays par
 * defaut MA ; langue preferee par defaut fr. Pas de suppression : archivage et restauration.
 */
@Entity
@Table(name = "customer")
public class Customer extends AuthoredAgencyEntity {

    public static final String DEFAULT_COUNTRY = "MA";
    public static final String DEFAULT_LOCALE = "fr";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 15)
    private String ice;

    @Column(name = "if_number", length = 20)
    private String ifNumber;

    @Column(length = 50)
    private String rc;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "preferred_locale", nullable = false, length = 5)
    private String preferredLocale;

    @Column(length = 100)
    private String sector;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CustomerSource source;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Customer() {
        // pour JPA
    }

    public static Customer create(CustomerData data) {
        Customer customer = new Customer();
        customer.apply(data);
        return customer;
    }

    /**
     * Remplace les champs modifiables. Passer en particulier efface ICE, IF et RC (le refus quand
     * l'entreprise a des contacts est une regle du cas d'usage, qui connait les contacts).
     */
    public void update(CustomerData data) {
        apply(data);
    }

    private void apply(CustomerData data) {
        this.type = Objects.requireNonNull(data.type(), "type");
        this.name = Texts.required(data.name(), "name");
        boolean company = type == CustomerType.COMPANY;
        this.ice = company ? Texts.trimToNull(data.ice()) : null;
        this.ifNumber = company ? Texts.trimToNull(data.ifNumber()) : null;
        this.rc = company ? Texts.trimToNull(data.rc()) : null;
        this.email = EmailAddresses.normalize(data.email());
        this.phone = PhoneNumbers.normalize(data.phone());
        this.addressLine = Texts.trimToNull(data.addressLine());
        this.city = Texts.trimToNull(data.city());
        String countryCode = Texts.trimToNull(data.country());
        this.country = countryCode == null ? DEFAULT_COUNTRY : countryCode;
        String locale = Texts.trimToNull(data.preferredLocale());
        this.preferredLocale = locale == null ? DEFAULT_LOCALE : locale;
        this.sector = Texts.trimToNull(data.sector());
        this.source = data.source();
        this.ownerUserId = data.ownerUserId();
        this.notes = Texts.trimToNull(data.notes());
    }

    /** @return vrai si le client vient d'etre archive (faux s'il l'etait deja) */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** @return vrai si le client vient d'etre restaure (faux s'il ne l'etait pas) */
    public boolean restore() {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        return true;
    }

    public boolean isCompany() {
        return type == CustomerType.COMPANY;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public CustomerType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getIce() {
        return ice;
    }

    public String getIfNumber() {
        return ifNumber;
    }

    public String getRc() {
        return rc;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
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

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public String getSector() {
        return sector;
    }

    public CustomerSource getSource() {
        return source;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
