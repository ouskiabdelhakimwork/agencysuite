package ma.agencysuite.crm.application;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/** Tris proposes pour la liste des demandes ; l'identifiant departage les egalites (pagination stable). */
public enum RequestSort {
    /** Les plus recemment creees d'abord (defaut). */
    CREATED_AT_DESC(Sort.by(Order.desc("createdAt"), Order.desc("id"))),
    /** Les plus recemment changees de statut d'abord. */
    STATUS_CHANGED_AT_DESC(Sort.by(Order.desc("statusChangedAt"), Order.desc("id"))),
    /** Par date de depart, la plus proche d'abord ; sans date en dernier (ordre PostgreSQL par defaut). */
    DEPARTURE_DATE(Sort.by(Order.asc("departureDate"), Order.asc("id")));

    private final Sort sort;

    RequestSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
