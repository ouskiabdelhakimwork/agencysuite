package ma.agencysuite.platform;

import java.util.Objects;
import java.util.Set;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;

/**
 * Une agence cliente vient d'etre creee depuis le back-office, avec son palier et ses modules.
 * Publie dans la transaction de la creation ; les {@link ModuleEnabled} des modules actives a la
 * creation sont publies aussi. Consomme par le journal d'audit (module iam).
 */
public record AgencyCreated(AgencyId agencyId, String planCode, Set<ModuleCode> modules) {

    public AgencyCreated {
        Objects.requireNonNull(agencyId, "agencyId");
        Objects.requireNonNull(planCode, "planCode");
        modules = Set.copyOf(modules);
    }
}
