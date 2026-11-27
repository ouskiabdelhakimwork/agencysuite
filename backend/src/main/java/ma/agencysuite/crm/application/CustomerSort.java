package ma.agencysuite.crm.application;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/** Tris proposes pour la liste des clients ; l'identifiant departage les egalites (pagination stable). */
public enum CustomerSort {
    /** Par nom, sans tenir compte de la casse (index lower(name)). */
    NAME(Sort.by(Order.asc("name").ignoreCase(), Order.asc("id"))),
    /** Les plus recemment crees d'abord. */
    CREATED_AT_DESC(Sort.by(Order.desc("createdAt"), Order.desc("id"))),
    /** Les plus recemment modifies d'abord. */
    UPDATED_AT_DESC(Sort.by(Order.desc("updatedAt"), Order.desc("id")));

    private final Sort sort;

    CustomerSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
