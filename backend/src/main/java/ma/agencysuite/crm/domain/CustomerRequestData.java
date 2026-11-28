package ma.agencysuite.crm.domain;

import java.time.LocalDate;
import java.util.UUID;
import ma.agencysuite.shared.money.Money;

/**
 * Champs modifiables d'une demande, tels que saisis (deja valides par l'API), hors statut.
 * {@code adults} et {@code children} nuls valent 1 et 0 ; {@code budget} est facultatif.
 */
public record CustomerRequestData(UUID customerId, UUID ownerUserId, RequestChannel channel, CustomerSource source,
        String destination, LocalDate departureDate, LocalDate returnDate, String datesNote, Integer adults,
        Integer children, Money budget, String description) {
}
