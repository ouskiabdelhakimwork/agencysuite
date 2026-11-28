package ma.agencysuite.crm.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.application.TravelerRegistry.TravelerView;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.crm.domain.Gender;
import ma.agencysuite.crm.domain.Traveler;
import org.springframework.data.domain.Page;

/**
 * Reponses de l'API des voyageurs. Jamais de {@code name_key} (cle technique). La date et le lieu
 * de naissance n'apparaissent que dans la fiche, et seulement pour un utilisateur qui a
 * {@code identity.read}.
 */
final class TravelerResponses {

    private TravelerResponses() {
    }

    /** Ligne de la liste : jamais de date ni de lieu de naissance. */
    record TravelerItem(UUID id, String lastName, String firstName, String lastNameAr, String firstNameAr,
            String nationality, String email, String phone, boolean archived) {

        static TravelerItem from(Traveler t) {
            return new TravelerItem(t.getId(), t.getLastName(), t.getFirstName(), t.getLastNameAr(),
                    t.getFirstNameAr(), t.getNationality(), t.getEmail(), t.getPhone(), t.isArchived());
        }
    }

    /** Page de voyageurs. */
    record TravelerPage(List<TravelerItem> items, int page, int size, long totalElements, int totalPages) {

        static TravelerPage from(Page<Traveler> result) {
            return new TravelerPage(result.map(TravelerItem::from).getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    /** Client rattache au voyageur. */
    record TravelerCustomer(UUID id, String name, CustomerType type, boolean archived) {

        static TravelerCustomer from(Customer c) {
            return new TravelerCustomer(c.getId(), c.getName(), c.getType(), c.isArchived());
        }
    }

    /** Fiche complete du voyageur. */
    record TravelerResponse(UUID id, String lastName, String firstName, String lastNameAr, String firstNameAr,
            Gender gender,
            @Schema(description = "Nulle sans la permission identity.read (voir birthDateHidden)") LocalDate birthDate,
            @Schema(description = "Vrai si une date de naissance existe mais est masquee (permission identity.read "
                    + "absente)") boolean birthDateHidden,
            @Schema(description = "Nul sans la permission identity.read (voir birthPlaceHidden)") String birthPlace,
            @Schema(description = "Vrai si un lieu de naissance existe mais est masque (permission identity.read "
                    + "absente)") boolean birthPlaceHidden,
            String nationality, String email, String phone, String dietary,
            String emergencyContactName, String emergencyContactPhone, String notes, boolean archived,
            Instant archivedAt, List<TravelerCustomer> customers, Long version, Instant createdAt,
            Instant updatedAt) {

        static TravelerResponse from(TravelerView view) {
            Traveler t = view.traveler();
            return new TravelerResponse(t.getId(), t.getLastName(), t.getFirstName(), t.getLastNameAr(),
                    t.getFirstNameAr(), t.getGender(), view.birthDate(), view.birthDateHidden(), view.birthPlace(),
                    view.birthPlaceHidden(), t.getNationality(), t.getEmail(), t.getPhone(), t.getDietary(),
                    t.getEmergencyContactName(), t.getEmergencyContactPhone(), t.getNotes(), t.isArchived(),
                    t.getArchivedAt(),
                    view.customers().stream().map(TravelerCustomer::from).toList(), t.getVersion(), t.getCreatedAt(),
                    t.getUpdatedAt());
        }
    }
}
