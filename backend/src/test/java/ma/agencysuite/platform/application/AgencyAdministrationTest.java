package ma.agencysuite.platform.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.platform.infrastructure.ModuleDefinitionRepository;
import ma.agencysuite.platform.infrastructure.PlanRepository;
import ma.agencysuite.platform.infrastructure.SubscriptionRepository;
import ma.agencysuite.shared.module.ModuleCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Creation concurrente du meme sous-domaine : une autre creation passe entre la verification
 * d'existence et l'insertion. La verification ne voit rien, la base refuse au flush
 * (uk_agency_subdomain) et le service doit repondre « sous-domaine deja pris » (409).
 * La reconnaissance de la vraie erreur PostgreSQL est couverte par AgencyConstraintsTest.
 */
class AgencyAdministrationTest {

    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final AgencyModuleRepository agencyModules = mock(AgencyModuleRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final ModuleDefinitionRepository moduleDefinitions = mock(ModuleDefinitionRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final AgencyAdministration administration =
            new AgencyAdministration(agencies, subscriptions, agencyModules, plans, moduleDefinitions, events);

    @BeforeEach
    void catalogKnowsThePlanAndModule() {
        when(plans.existsById("PRO")).thenReturn(true);
        when(moduleDefinitions.existsById("TRAVEL")).thenReturn(true);
        // La creation concurrente n'est pas encore validee : la verification ne la voit pas.
        when(agencies.existsBySubdomain("atlas")).thenReturn(false);
    }

    @Test
    void concurrentCreationOfTheSameSubdomainBecomesSubdomainTaken() {
        when(agencies.saveAndFlush(any(Agency.class))).thenThrow(violationOf("uk_agency_subdomain"));

        assertThatThrownBy(() -> administration.createAgency(command()))
                .isInstanceOf(SubdomainAlreadyTakenException.class);
        verifyNoInteractions(subscriptions, agencyModules, events);
    }

    @Test
    void anyOtherIntegrityViolationIsNotHidden() {
        DataIntegrityViolationException other = violationOf("ck_some_other_rule");
        when(agencies.saveAndFlush(any(Agency.class))).thenThrow(other);

        assertThatThrownBy(() -> administration.createAgency(command())).isSameAs(other);
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraint));
    }

    private static CreateAgencyCommand command() {
        return new CreateAgencyCommand("Atlas Voyages", "atlas", null, null, null, null, null, null, "PRO",
                SubscriptionPeriod.MONTHLY, LocalDate.of(2026, 12, 1), List.of(ModuleCode.TRAVEL));
    }
}
