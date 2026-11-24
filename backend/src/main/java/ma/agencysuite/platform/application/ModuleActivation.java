package ma.agencysuite.platform.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.platform.ModuleDisabled;
import ma.agencysuite.platform.ModuleEnabled;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.AgencyModuleId;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.platform.infrastructure.ModuleDefinitionRepository;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activation et desactivation d'un module pour une agence (US-102).
 *
 * <p>Les deux operations sont idempotentes. La ligne agency_module n'est jamais supprimee :
 * la reactivation rend les donnees du module a nouveau visibles. Un evenement
 * {@link ModuleEnabled} / {@link ModuleDisabled} est publie seulement quand l'etat change
 * (trace consommee par le journal d'audit a l'etape 3).
 *
 * <p>S'execute uniquement dans le perimetre plateforme (back-office, jeton du realm "platform") :
 * ailleurs, {@link IllegalStateException} (500), avant toute lecture ou ecriture.
 */
@Service
public class ModuleActivation {

    private final AgencyRepository agencies;
    private final AgencyModuleRepository agencyModules;
    private final ModuleDefinitionRepository moduleDefinitions;
    private final ApplicationEventPublisher events;

    ModuleActivation(AgencyRepository agencies, AgencyModuleRepository agencyModules,
            ModuleDefinitionRepository moduleDefinitions, ApplicationEventPublisher events) {
        this.agencies = agencies;
        this.agencyModules = agencyModules;
        this.moduleDefinitions = moduleDefinitions;
        this.events = events;
    }

    /** Active le module (cree la souscription ou la reactive). Sans effet s'il est deja actif. */
    @Transactional
    public void enable(UUID agencyId, ModuleCode module) {
        requireAgencyAndModule(agencyId, module);
        Instant now = Instant.now();
        Optional<AgencyModule> existing = agencyModules.findById(new AgencyModuleId(agencyId, module.name()));
        if (existing.isPresent()) {
            if (existing.get().isEnabled()) {
                return;
            }
            existing.get().reenable(now);
        } else {
            agencyModules.save(AgencyModule.enable(agencyId, module, now));
        }
        events.publishEvent(new ModuleEnabled(new AgencyId(agencyId), module, now));
    }

    /** Desactive le module sans supprimer la souscription ni les donnees. Sans effet s'il est inactif. */
    @Transactional
    public void disable(UUID agencyId, ModuleCode module) {
        requireAgencyAndModule(agencyId, module);
        Optional<AgencyModule> existing = agencyModules.findById(new AgencyModuleId(agencyId, module.name()));
        if (existing.isEmpty() || !existing.get().isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        existing.get().disable(now);
        events.publishEvent(new ModuleDisabled(new AgencyId(agencyId), module, now));
    }

    private void requireAgencyAndModule(UUID agencyId, ModuleCode module) {
        TenantContext.requirePlatform();
        if (!agencies.existsById(agencyId)) {
            throw new AgencyNotFoundException(agencyId);
        }
        if (!moduleDefinitions.existsById(module.name())) {
            throw new ModuleNotFoundException(module.name());
        }
    }
}
