package ma.agencysuite.crm.application;

import ma.agencysuite.crm.domain.RequestStatus;

/**
 * Changement de statut d'une demande non autorise par les transitions de {@link RequestStatus} :
 * 409 invalid-status-transition, avec les statuts de depart et d'arrivee en code.
 */
public class InvalidStatusTransitionException extends CrmException {

    private final RequestStatus from;
    private final RequestStatus to;

    public InvalidStatusTransitionException(RequestStatus from, RequestStatus to) {
        super("Transition de statut refusee : " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public RequestStatus getFrom() {
        return from;
    }

    public RequestStatus getTo() {
        return to;
    }
}
