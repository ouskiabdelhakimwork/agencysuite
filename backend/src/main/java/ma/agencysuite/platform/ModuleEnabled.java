package ma.agencysuite.platform;

import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Un module vient d'etre active pour une agence (creation de l'agence ou reactivation).
 * Publie uniquement quand l'etat change, dans la transaction de l'action. Consomme par le journal
 * d'audit (module iam), qui ecrit son entree dans cette meme transaction.
 */
public record ModuleEnabled(AgencyId agencyId, ModuleCode module, Instant at) {

    public ModuleEnabled {
        Objects.requireNonNull(agencyId, "agencyId");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(at, "at");
    }
}
