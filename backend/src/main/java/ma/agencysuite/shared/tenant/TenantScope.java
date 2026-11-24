package ma.agencysuite.shared.tenant;

import java.util.Objects;

/**
 * Perimetre d'execution courant : une agence precise, ou la plateforme (back-office
 * editeur, qui voit toutes les agences).
 */
public sealed interface TenantScope {

    /** Execution pour le compte d'une agence : seules ses donnees sont visibles. */
    record Agency(AgencyId agencyId) implements TenantScope {
        public Agency {
            Objects.requireNonNull(agencyId, "agencyId");
        }
    }

    /** Execution pour le compte de l'editeur (super-administrateur, taches de plateforme). */
    record Platform() implements TenantScope {
    }
}
