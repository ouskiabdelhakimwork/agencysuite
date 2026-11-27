package ma.agencysuite.crm.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.application.CustomerRegistry.CustomerView;
import ma.agencysuite.crm.domain.Contact;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerSource;
import ma.agencysuite.crm.domain.CustomerType;
import org.springframework.data.domain.Page;

/** Reponses de l'API des clients. */
final class CustomerResponses {

    private CustomerResponses() {
    }

    /** Ligne de la liste. */
    record CustomerItem(UUID id, CustomerType type, String name, String email, String phone, String city,
            UUID ownerUserId, boolean archived) {

        static CustomerItem from(Customer c) {
            return new CustomerItem(c.getId(), c.getType(), c.getName(), c.getEmail(), c.getPhone(), c.getCity(),
                    c.getOwnerUserId(), c.isArchived());
        }
    }

    /** Page de clients. */
    record CustomerPage(List<CustomerItem> items, int page, int size, long totalElements, int totalPages) {

        static CustomerPage from(Page<Customer> result) {
            return new CustomerPage(result.map(CustomerItem::from).getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    /** Contact d'un client entreprise. */
    record ContactResponse(UUID id, String fullName, String jobTitle, String email, String phone, boolean isPrimary,
            Long version) {

        static ContactResponse from(Contact c) {
            return new ContactResponse(c.getId(), c.getFullName(), c.getJobTitle(), c.getEmail(), c.getPhone(),
                    c.isPrimary(), c.getVersion());
        }
    }

    /** Fiche complete du client. */
    record CustomerResponse(UUID id, CustomerType type, String name, String ice, String ifNumber, String rc,
            String email, String phone, String addressLine, String city, String country, String preferredLocale,
            String sector, CustomerSource source, UUID ownerUserId, String notes, boolean archived,
            Instant archivedAt, List<ContactResponse> contacts, Long version, Instant createdAt, Instant updatedAt) {

        static CustomerResponse from(CustomerView view) {
            Customer c = view.customer();
            return new CustomerResponse(c.getId(), c.getType(), c.getName(), c.getIce(), c.getIfNumber(), c.getRc(),
                    c.getEmail(), c.getPhone(), c.getAddressLine(), c.getCity(), c.getCountry(),
                    c.getPreferredLocale(), c.getSector(), c.getSource(), c.getOwnerUserId(), c.getNotes(),
                    c.isArchived(), c.getArchivedAt(), view.contacts().stream().map(ContactResponse::from).toList(),
                    c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
