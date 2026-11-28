package ma.agencysuite.supplier.application;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/** Tris proposes pour les listes du module ; l'identifiant departage les egalites (pagination stable). */
public final class CatalogSort {

    private CatalogSort() {
    }

    /** Tris de la liste des fournisseurs. */
    public enum SupplierSort {
        /** Par nom sans accents ({@code name_key}). */
        NAME(Sort.by(Order.asc("nameKey"), Order.asc("id"))),
        /** Les plus recemment crees d'abord. */
        CREATED_AT_DESC(Sort.by(Order.desc("createdAt"), Order.desc("id"))),
        /** Les plus recemment modifies d'abord. */
        UPDATED_AT_DESC(Sort.by(Order.desc("updatedAt"), Order.desc("id")));

        private final Sort sort;

        SupplierSort(Sort sort) {
            this.sort = sort;
        }

        public Sort sort() {
            return sort;
        }
    }

    /** Tris de la liste des prestations. */
    public enum ProductSort {
        /** Par nom ({@code search_key} : premiere langue presente dans l'ordre fr, ar, en). */
        NAME(Sort.by(Order.asc("searchKey"), Order.asc("id"))),
        /** Les plus recemment modifiees d'abord. */
        UPDATED_AT_DESC(Sort.by(Order.desc("updatedAt"), Order.desc("id")));

        private final Sort sort;

        ProductSort(Sort sort) {
            this.sort = sort;
        }

        public Sort sort() {
            return sort;
        }
    }
}
