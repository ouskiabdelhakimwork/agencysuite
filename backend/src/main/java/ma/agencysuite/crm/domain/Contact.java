package ma.agencysuite.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;

/**
 * Personne a contacter dans une entreprise cliente. Au plus un contact principal par client
 * (index unique partiel en base) ; le cas d'usage retire le statut a l'ancien principal.
 * Le client est reference par son identifiant : la cle composee (customer_id, agency_id) garantit
 * en base que le contact reste dans l'agence de son client.
 */
@Entity
@Table(name = "contact")
public class Contact extends AuthoredAgencyEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected Contact() {
        // pour JPA
    }

    public Contact(UUID customerId, ContactData data) {
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        update(data);
    }

    public void update(ContactData data) {
        this.fullName = Texts.required(data.fullName(), "fullName");
        this.jobTitle = Texts.trimToNull(data.jobTitle());
        this.email = EmailAddresses.normalize(data.email());
        this.phone = PhoneNumbers.normalize(data.phone());
        this.primary = data.primary();
    }

    /** Retire le statut de contact principal (un autre contact le devient). */
    public void demote() {
        this.primary = false;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isPrimary() {
        return primary;
    }
}
