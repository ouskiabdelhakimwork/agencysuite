package ma.agencysuite.platform;

import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Un module vient d'etre active pour une agence (creation de l'agence ou reactivation).
 * Publie uniquement quand l'etat change. Sera consomme par le journal d'audit (etape 3).
 */
public record ModuleEnabled(AgencyId agencyId, ModuleCode module, Instant at) {

    public ModuleEnabled {
        Objects.requireNonNull(agencyId, "agencyId");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(at, "at");
    }
}
