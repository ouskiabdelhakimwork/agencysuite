package ma.agencysuite.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import ma.agencysuite.shared.contact.EmailAddresses;
import ma.agencysuite.shared.contact.PhoneNumbers;
import ma.agencysuite.shared.persistence.AuthoredAgencyEntity;

/**
 * Voyageur de l'agence (etape 5, lot 2) : identite en lettres latines et arabes, date de naissance,
 * nationalite, coordonnees, regime alimentaire, personne a prevenir. Rattache a zero, un ou
 * plusieurs clients ({@link CustomerTraveler}).
 *
 * <p>Regles portees par l'entite : espaces retires ; e-mail en minuscules ; telephones (voyageur et
 * personne a prevenir) en E.164 ; {@code name_key} recalcule a chaque ecriture. La date et le
 * lieu de naissance ne sont remplaces que si le cas d'usage l'autorise (permission
 * {@code identity.read}).
 * Aucune piece d'identite ici (etape 4). Pas de suppression : archivage et restauration.
 */
@Entity
@Table(name = "traveler")
public class Traveler extends AuthoredAgencyEntity {

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name_ar", length = 100)
    private String lastNameAr;

    @Column(name = "first_name_ar", length = 100)
    private String firstNameAr;

    @Column(name = "name_key", nullable = false, length = 210)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 100)
    private String birthPlace;

    @Column(length = 2)
    private String nationality;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String dietary;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(length = 2000)
    private String notes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Reserve a l'etape 13b (anonymisation) : jamais rempli a ce lot. */
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    protected Traveler() {
        // pour JPA
    }

    /**
     * Nouveau voyageur.
     *
     * @param withBirthFields vrai si la date et le lieu de naissance de {@code data} doivent etre
     *        enregistres (permission verifiee par le cas d'usage) ; faux : ni date ni lieu
     */
    public static Traveler create(TravelerData data, boolean withBirthFields) {
        Traveler traveler = new Traveler();
        traveler.update(data, withBirthFields);
        return traveler;
    }

    /**
     * Remplace les champs modifiables.
     *
     * @param replaceBirthFields vrai : la date et le lieu de naissance de {@code data} remplacent les
     *        anciens (nuls : effaces) ; faux : la date et le lieu enregistres ne changent pas
     */
    public void update(TravelerData data, boolean replaceBirthFields) {
        this.lastName = Texts.required(data.lastName(), "lastName");
        this.firstName = Texts.required(data.firstName(), "firstName");
        this.lastNameAr = Texts.trimToNull(data.lastNameAr());
        this.firstNameAr = Texts.trimToNull(data.firstNameAr());
        this.nameKey = NameKeys.of(lastName, firstName);
        this.gender = data.gender();
        if (replaceBirthFields) {
            this.birthDate = data.birthDate();
            this.birthPlace = Texts.trimToNull(data.birthPlace());
        }
        this.nationality = Texts.trimToNull(data.nationality());
        this.email = EmailAddresses.normalize(data.email());
        this.phone = PhoneNumbers.normalize(data.phone());
        this.dietary = Texts.trimToNull(data.dietary());
        this.emergencyContactName = Texts.trimToNull(data.emergencyContactName());
        this.emergencyContactPhone = PhoneNumbers.normalize(data.emergencyContactPhone());
        this.notes = Texts.trimToNull(data.notes());
    }

    /** @return vrai si le voyageur vient d'etre archive (faux s'il l'etait deja) */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** @return vrai si le voyageur vient d'etre restaure (faux s'il ne l'etait pas) */
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

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastNameAr() {
        return lastNameAr;
    }

    public String getFirstNameAr() {
        return firstNameAr;
    }

    /** Cle technique de recherche ; jamais renvoyee par l'API. */
    public String getNameKey() {
        return nameKey;
    }

    public Gender getGender() {
        return gender;
    }

    /**
     * Date de naissance en clair. A ne renvoyer qu'a un utilisateur qui a {@code identity.read}
     * (la fiche du cas d'usage la masque sinon).
     */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /** Lieu de naissance : meme regle que {@link #getBirthDate()} (permission {@code identity.read}). */
    public String getBirthPlace() {
        return birthPlace;
    }

    public String getNationality() {
        return nationality;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getDietary() {
        return dietary;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
