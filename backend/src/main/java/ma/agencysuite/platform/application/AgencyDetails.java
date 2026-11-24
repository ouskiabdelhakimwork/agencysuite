package ma.agencysuite.platform.application;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.shared.module.ModuleCode;

/** Agence avec son abonnement actif (s'il existe) et ses modules actives (ordre du catalogue). */
public record AgencyDetails(Agency agency, Optional<Subscription> activeSubscription, Set<ModuleCode> enabledModules) {

    public AgencyDetails {
        EnumSet<ModuleCode> sorted = EnumSet.noneOf(ModuleCode.class);
        sorted.addAll(enabledModules);
        enabledModules = java.util.Collections.unmodifiableSet(sorted);
    }
}
