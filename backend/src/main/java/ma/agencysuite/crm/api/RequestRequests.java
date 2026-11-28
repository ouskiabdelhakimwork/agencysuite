package ma.agencysuite.crm.api;

import static ma.agencysuite.crm.api.Inputs.blankToNull;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import ma.agencysuite.crm.domain.CustomerRequest;
import ma.agencysuite.crm.domain.CustomerRequestData;
import ma.agencysuite.crm.domain.CustomerSource;
import ma.agencysuite.crm.domain.LostReason;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.crm.domain.RequestStatus;
import ma.agencysuite.shared.money.Money;
import ma.agencysuite.shared.money.MoneyJson;

/**
 * Corps des requetes de creation, de modification et de changement de statut d'une demande.
 * Le besoin (destination, dates, personnes, budget, description) est commun a la creation et a la
 * modification.
 */
final class RequestRequests {

    /** Devise du budget quand elle n'est pas envoyee. */
    static final String DEFAULT_BUDGET_CURRENCY = "MAD";

    private RequestRequests() {
    }

    /** Champs du besoin exprime, communs aux deux corps. */
    interface Need {
        LocalDate departureDate();

        LocalDate returnDate();

        Integer adults();

        Integer children();

        /** Date de retour posterieure ou egale a la date de depart, si les deux sont presentes. */
        @AssertTrue
        @Schema(hidden = true)
        default boolean isReturnAfterDeparture() {
            return departureDate() == null || returnDate() == null || !returnDate().isBefore(departureDate());
        }

        /** Au moins une personne (adultes + enfants, valeurs par defaut 1 et 0). */
        @AssertTrue
        @Schema(hidden = true)
        default boolean isAtLeastOnePerson() {
            int adultCount = adults() == null ? CustomerRequest.DEFAULT_ADULTS : adults();
            int childCount = children() == null ? CustomerRequest.DEFAULT_CHILDREN : children();
            return adultCount + childCount >= 1;
        }
    }

    /** POST /api/v1/requests. Sans {@code ownerUserId} : l'utilisateur courant. */
    record CreateRequestRequest(
            @Schema(description = "Client existant et non archive de l'agence") @NotNull UUID customerId,
            @Schema(description = "Comment la demande est arrivee") @NotNull RequestChannel channel,
            @Schema(description = "Origine commerciale (meme liste que pour les clients)") CustomerSource source,
            @Schema(description = "Utilisateur responsable, de la meme agence ; par defaut l'utilisateur courant")
            UUID ownerUserId,
            @Size(max = 200) String destination,
            @Schema(description = "Date de depart souhaitee (une date passee est acceptee)") LocalDate departureDate,
            @Schema(description = "Date de retour souhaitee, apres ou egale a la date de depart") LocalDate returnDate,
            @Schema(description = "Dates approximatives en texte libre (mi-juillet, environ une semaine)")
            @Size(max = 200) String datesNote,
            @Schema(description = "0 a 999, 1 par defaut ; au moins une personne au total")
            @Min(0) @Max(CustomerRequest.MAX_PEOPLE) Integer adults,
            @Schema(description = "0 a 999, 0 par defaut") @Min(0) @Max(CustomerRequest.MAX_PEOPLE) Integer children,
            @Schema(description = "Budget indicatif, strictement positif, MAD par defaut, sans arrondi")
            @Valid MoneyJson budget,
            @Size(max = 4000) String description) implements Need {

        CreateRequestRequest {
            destination = blankToNull(destination);
            datesNote = blankToNull(datesNote);
            description = blankToNull(description);
        }

        CustomerRequestData toData() {
            return new CustomerRequestData(customerId, ownerUserId, channel, source, destination, departureDate,
                    returnDate, datesNote, adults, children, budgetOf(budget), description);
        }
    }

    /**
     * PUT /api/v1/requests/{id} : remplace le client, le canal, la source, le responsable et le
     * besoin ; jamais le statut (operation dedicee). Responsable obligatoire (celui de la fiche lue).
     */
    record UpdateRequestRequest(
            @Schema(description = "Version de la fiche lue (verrouillage optimiste)") @NotNull Long version,
            @NotNull UUID customerId,
            @NotNull RequestChannel channel,
            CustomerSource source,
            @NotNull UUID ownerUserId,
            @Size(max = 200) String destination,
            LocalDate departureDate,
            LocalDate returnDate,
            @Size(max = 200) String datesNote,
            @Min(0) @Max(CustomerRequest.MAX_PEOPLE) Integer adults,
            @Min(0) @Max(CustomerRequest.MAX_PEOPLE) Integer children,
            @Valid MoneyJson budget,
            @Size(max = 4000) String description) implements Need {

        UpdateRequestRequest {
            destination = blankToNull(destination);
            datesNote = blankToNull(datesNote);
            description = blankToNull(description);
        }

        CustomerRequestData toData() {
            return new CustomerRequestData(customerId, ownerUserId, channel, source, destination, departureDate,
                    returnDate, datesNote, adults, children, budgetOf(budget), description);
        }
    }

    /**
     * POST /api/v1/requests/{id}/status. {@code lostReason} obligatoire pour LOST ; raison et
     * commentaire ignores pour un autre statut.
     */
    record StatusChangeRequest(
            @Schema(description = "Version de la fiche lue (verrouillage optimiste)") @NotNull Long version,
            @NotNull RequestStatus status,
            @Schema(description = "Obligatoire pour LOST") LostReason lostReason,
            @Schema(description = "Facultatif, pour LOST seulement") @Size(max = 500) String lostComment) {

        StatusChangeRequest {
            lostComment = blankToNull(lostComment);
        }

        /** Une demande perdue a toujours une raison. */
        @AssertTrue
        @Schema(hidden = true)
        boolean isLostReasonGivenWhenLost() {
            return status != RequestStatus.LOST || lostReason != null;
        }

        LostReason reason() {
            return status == RequestStatus.LOST ? lostReason : null;
        }

        String comment() {
            return status == RequestStatus.LOST ? lostComment : null;
        }
    }

    private static Money budgetOf(MoneyJson budget) {
        return budget == null ? null : budget.toPositiveMoney(DEFAULT_BUDGET_CURRENCY);
    }
}
