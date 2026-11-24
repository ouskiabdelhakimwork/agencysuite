package ma.agencysuite.platform;

import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Un module vient d'etre desactive pour une agence (ses donnees sont conservees).
 * Publie uniquement quand l'etat change. Sera consomme par le journal d'audit (etape 3).
 */
public record ModuleDisabled(AgencyId agencyId, ModuleCode module, Instant at) {

    public ModuleDisabled {
        Objects.requireNonNull(agencyId, "agencyId");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(at, "at");
    }
}
