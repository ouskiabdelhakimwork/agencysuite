package ma.agencysuite.platform.application;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.platform.ModuleEnabled;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionStatus;
import ma.agencysuite.platform.infrastructure.AgencyConstraints;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.platform.infrastructure.ModuleDefinitionRepository;
import ma.agencysuite.platform.infrastructure.PlanRepository;
import ma.agencysuite.platform.infrastructure.SubscriptionRepository;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration des agences clientes par l'editeur (US-101).
 * S'execute dans le perimetre plateforme (ouvert par le filtre du back-office).
 */
@Service
public class AgencyAdministration {

    private final AgencyRepository agencies;
    private final SubscriptionRepository subscriptions;
    private final AgencyModuleRepository agencyModules;
    private final PlanRepository plans;
    private final ModuleDefinitionRepository moduleDefinitions;
    private final ApplicationEventPublisher events;

    AgencyAdministration(AgencyRepository agencies, SubscriptionRepository subscriptions,
            AgencyModuleRepository agencyModules, PlanRepository plans, ModuleDefinitionRepository moduleDefinitions,
            ApplicationEventPublisher events) {
        this.agencies = agencies;
        this.subscriptions = subscriptions;
        this.agencyModules = agencyModules;
        this.plans = plans;
        this.moduleDefinitions = moduleDefinitions;
        this.events = events;
    }

    /**
     * Cree l'agence, son identite legale, son abonnement et active les modules demandes,
     * en une seule transaction. Un evenement {@link ModuleEnabled} est publie par module active.
     *
     * @throws InvalidSubdomainException sous-domaine invalide ou reserve (regles de {@link Agency})
     * @throws SubdomainAlreadyTakenException sous-domaine deja attribue, y compris par une creation
     *         concurrente (violation de la contrainte unique detectee au flush)
     * @throws PlanNotFoundException palier inconnu
     * @throws ModuleNotFoundException module absent du catalogue
     */
    @Transactional
    public AgencyDetails createAgency(CreateAgencyCommand command) {
        Agency.checkSubdomain(command.subdomain()).ifPresent(violation -> {
            throw new InvalidSubdomainException(violation);
        });
        Agency agency = Agency.createCustomer(command.name(), command.subdomain());
        if (agencies.existsBySubdomain(agency.getSubdomain())) {
            throw new SubdomainAlreadyTakenException(agency.getSubdomain());
        }
        if (command.planCode() == null || !plans.existsById(command.planCode())) {
            throw new PlanNotFoundException(command.planCode());
        }
        Set<ModuleCode> modules = new LinkedHashSet<>(command.modules());
        for (ModuleCode module : modules) {
            if (!moduleDefinitions.existsById(module.name())) {
                throw new ModuleNotFoundException(module.name());
            }
        }

        agency.updateLegalIdentity(command.legalName(), command.ice(), command.ifNumber(), command.rc(),
                command.patente(), command.address());
        // Flush immediat : une creation concurrente du meme sous-domaine (passee entre la
        // verification ci-dessus et l'insertion) est detectee ici et traduite en 409.
        try {
            agencies.saveAndFlush(agency);
        } catch (DataIntegrityViolationException e) {
            if (AgencyConstraints.isSubdomainTaken(e)) {
                throw new SubdomainAlreadyTakenException(agency.getSubdomain());
            }
            throw e;
        }
        Subscription subscription = subscriptions.save(
                Subscription.start(agency.getId(), command.planCode(), command.period(), command.startDate()));

        Instant now = Instant.now();
        AgencyId agencyId = new AgencyId(agency.getId());
        for (ModuleCode module : modules) {
            agencyModules.save(AgencyModule.enable(agency.getId(), module, now));
            events.publishEvent(new ModuleEnabled(agencyId, module, now));
        }
        return new AgencyDetails(agency, Optional.of(subscription), modules);
    }

    /** Agence, abonnement actif et modules actives. */
    @Transactional(readOnly = true)
    public AgencyDetails getAgency(UUID agencyId) {
        Agency agency = agencies.findById(agencyId).orElseThrow(() -> new AgencyNotFoundException(agencyId));
        Set<ModuleCode> modules = EnumSet.noneOf(ModuleCode.class);
        agencyModules.findByIdAgencyIdAndEnabledTrue(agencyId).forEach(m -> modules.add(m.getModule()));
        return new AgencyDetails(agency,
                subscriptions.findByAgencyIdAndStatus(agencyId, SubscriptionStatus.ACTIVE), modules);
    }
}
