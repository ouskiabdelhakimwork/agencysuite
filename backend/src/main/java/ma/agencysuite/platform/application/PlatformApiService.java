package ma.agencysuite.platform.application;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.AgencyModuleId;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation de {@link PlatformApi}.
 *
 * <p>Ne lit que ce que le perimetre courant a le droit de voir ; elle n'ouvre jamais de
 * perimetre elle-meme :
 * <ul>
 *   <li>perimetre agence : uniquement l'agence courante ; demander une autre agence renvoie
 *       {@code false} / un ensemble vide, sans aucune lecture ;</li>
 *   <li>perimetre plateforme : toute agence (l'appelant y est deja, back-office) ;</li>
 *   <li>aucun perimetre : rien ({@code false} / vide), comme partout ailleurs.</li>
 * </ul>
 * La Row Level Security reste la seconde barriere en base.
 *
 * <p>Pas de cache dans ce lot (a envisager plus tard, avec invalidation a l'activation/desactivation).
 */
@Service
class PlatformApiService implements PlatformApi {

    private final AgencyRepository agencies;
    private final AgencyModuleRepository agencyModules;
    private final TransactionTemplate readOnly;

    PlatformApiService(AgencyRepository agencies, AgencyModuleRepository agencyModules,
            PlatformTransactionManager transactionManager) {
        this.agencies = agencies;
        this.agencyModules = agencyModules;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public boolean isModuleEnabled(AgencyId agencyId, ModuleCode module) {
        if (!currentScopeMayRead(agencyId)) {
            return false;
        }
        Boolean enabled = readOnly.execute(status -> agencyModules
                .findById(new AgencyModuleId(agencyId.value(), module.name()))
                .map(AgencyModule::isEnabled)
                .orElse(false));
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public Set<ModuleCode> enabledModules(AgencyId agencyId) {
        if (!currentScopeMayRead(agencyId)) {
            return Set.of();
        }
        Set<ModuleCode> modules = EnumSet.noneOf(ModuleCode.class);
        readOnly.executeWithoutResult(status -> agencyModules.findByIdAgencyIdAndEnabledTrue(agencyId.value())
                .forEach(m -> modules.add(m.getModule())));
        return Collections.unmodifiableSet(modules);
    }

    @Override
    public boolean isOperational(AgencyId agencyId) {
        if (!currentScopeMayRead(agencyId)) {
            return false;
        }
        Boolean operational = readOnly.execute(status -> agencies.findById(agencyId.value())
                .map(Agency::isOperational)
                .orElse(false));
        return Boolean.TRUE.equals(operational);
    }

    private static boolean currentScopeMayRead(AgencyId agencyId) {
        return TenantContext.current()
                .map(scope -> switch (scope) {
                    case TenantScope.Platform platform -> true;
                    case TenantScope.Agency agency -> agency.agencyId().equals(agencyId);
                })
                .orElse(false);
    }
}
