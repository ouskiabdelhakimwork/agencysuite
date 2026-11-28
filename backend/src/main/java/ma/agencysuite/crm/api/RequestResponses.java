package ma.agencysuite.crm.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.crm.application.CustomerRequestRegistry.RequestView;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.domain.CustomerRequest;
import ma.agencysuite.crm.domain.CustomerSource;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.crm.domain.LostReason;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.crm.domain.RequestStatus;
import ma.agencysuite.shared.money.Money;
import ma.agencysuite.shared.money.MoneyJson;
import org.springframework.data.domain.Page;

/** Reponses de l'API des demandes. La cle de recherche n'est jamais renvoyee. */
final class RequestResponses {

    private RequestResponses() {
    }

    /** Client de la demande, en resume (archive ou non). */
    record RequestCustomer(UUID id, String name, CustomerType type) {

        static RequestCustomer from(Customer c) {
            return new RequestCustomer(c.getId(), c.getName(), c.getType());
        }
    }

    /** Ligne de la liste. */
    record RequestItem(UUID id, RequestStatus status, Instant statusChangedAt, RequestCustomer customer,
            UUID ownerUserId, RequestChannel channel, String destination, LocalDate departureDate,
            LocalDate returnDate, int adults, int children, Instant createdAt) {

        static RequestItem from(RequestView view) {
            CustomerRequest r = view.request();
            return new RequestItem(r.getId(), r.getStatus(), r.getStatusChangedAt(),
                    RequestCustomer.from(view.customer()), r.getOwnerUserId(), r.getChannel(), r.getDestination(),
                    r.getDepartureDate(), r.getReturnDate(), r.getAdults(), r.getChildren(), r.getCreatedAt());
        }
    }

    /** Page de demandes. */
    record RequestPage(List<RequestItem> items, int page, int size, long totalElements, int totalPages) {

        static RequestPage from(Page<RequestView> result) {
            return new RequestPage(result.map(RequestItem::from).getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    /** Fiche complete de la demande. */
    record RequestResponse(UUID id, RequestCustomer customer, UUID ownerUserId, RequestChannel channel,
            CustomerSource source, RequestStatus status, Instant statusChangedAt, LostReason lostReason,
            String lostComment, String destination, LocalDate departureDate, LocalDate returnDate, String datesNote,
            int adults, int children, MoneyJson budget, String description, Long version, Instant createdAt,
            Instant updatedAt) {

        static RequestResponse from(RequestView view) {
            CustomerRequest r = view.request();
            Money budget = r.getBudget();
            return new RequestResponse(r.getId(), RequestCustomer.from(view.customer()), r.getOwnerUserId(),
                    r.getChannel(), r.getSource(), r.getStatus(), r.getStatusChangedAt(), r.getLostReason(),
                    r.getLostComment(), r.getDestination(), r.getDepartureDate(), r.getReturnDate(),
                    r.getDatesNote(), r.getAdults(), r.getChildren(), budget == null ? null : MoneyJson.from(budget),
                    r.getDescription(), r.getVersion(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
