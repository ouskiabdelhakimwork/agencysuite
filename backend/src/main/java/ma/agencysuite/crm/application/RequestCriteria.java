package ma.agencysuite.crm.application;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.crm.domain.RequestStatus;

/**
 * Criteres de la liste des demandes ; chacun est facultatif (vide ou nul : pas de filtre).
 *
 * @param statuses l'un de ces statuts
 * @param createdFrom creees a partir de ce jour (inclus, UTC)
 * @param createdTo creees jusqu'a ce jour (inclus, UTC)
 * @param q texte recherche dans la destination et le nom du client
 */
public record RequestCriteria(Set<RequestStatus> statuses, UUID ownerUserId, UUID customerId, RequestChannel channel,
        LocalDate createdFrom, LocalDate createdTo, String q) {
}
