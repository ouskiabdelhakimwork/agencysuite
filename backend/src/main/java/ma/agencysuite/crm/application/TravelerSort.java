package ma.agencysuite.crm.application;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/** Tris proposes pour la liste des voyageurs ; l'identifiant departage les egalites (pagination stable). */
public enum TravelerSort {
    /** Par cle de nom (minuscules, sans accents : index (agency_id, name_key)). */
    NAME(Sort.by(Order.asc("nameKey"), Order.asc("id"))),
    /** Les plus recemment crees d'abord. */
    CREATED_AT_DESC(Sort.by(Order.desc("createdAt"), Order.desc("id"))),
    /** Les plus recemment modifies d'abord. */
    UPDATED_AT_DESC(Sort.by(Order.desc("updatedAt"), Order.desc("id")));

    private final Sort sort;

    TravelerSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
