package ma.agencysuite.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.platform.domain.AgencyStatus;
import ma.agencysuite.platform.domain.ModuleDefinition;
import ma.agencysuite.platform.domain.Plan;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.platform.domain.SubscriptionStatus;
import ma.agencysuite.platform.infrastructure.AgencyModuleRepository;
import ma.agencysuite.platform.infrastructure.AgencyRepository;
import ma.agencysuite.platform.infrastructure.ModuleDefinitionRepository;
import ma.agencysuite.platform.infrastructure.PlanRepository;
import ma.agencysuite.platform.infrastructure.SubscriptionRepository;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Migrations Flyway et correspondance entites / schema sur une vraie base PostgreSQL.
 * Le demarrage du contexte verifie deja que Hibernate valide le schema (ddl-auto: validate).
 *
 * <p>Les tables de la plateforme sont protegees par la Row Level Security : les tests
 * s'executent dans le perimetre plateforme (back-office), dans une transaction annulee a la fin.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlatformPersistenceTest {

    @Autowired
    private PlanRepository plans;
    @Autowired
    private ModuleDefinitionRepository modules;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private AgencyModuleRepository agencyModules;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transaction;

    @Test
    void seedsTheThreePlansOfTheCore() {
        inPlatformTransaction(() -> {
            List<Plan> all = plans.findAll();

            assertThat(all).extracting(Plan::getCode).containsExactlyInAnyOrder("STARTER", "PRO", "BUSINESS");
            Plan business = plans.findById("BUSINESS").orElseThrow();
            assertThat(business.getMaxUsers()).isEmpty(); // illimite
            assertThat(business.getStorageQuotaGb()).isEqualTo(500);
            assertThat(business.getPriceMonthly()).isEmpty(); // prix pas encore defini
            assertThat(plans.findById("STARTER").orElseThrow().getMaxUsers()).hasValue(3);
        });
    }

    @Test
    void catalogContainsEveryModuleCode() {
        inPlatformTransaction(() -> assertThat(modules.findAll())
                .extracting(ModuleDefinition::getCode)
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(ModuleCode.values())));
    }

    @Test
    void persistsAnAgencyWithSubscriptionAndModules() {
        inPlatformTransaction(() -> {
            Agency agency = agencies.save(Agency.createCustomer("Atlas Voyages", "atlas-voyages"));
            agency.updateLegalIdentity("Atlas Voyages SARL", "001234567000089", "12345678", "98765", "123456",
                    "12, avenue Mohammed V, Marrakech");
            subscriptions.save(Subscription.start(agency.getId(), "PRO", SubscriptionPeriod.YEARLY, LocalDate.of(2026, 12, 1)));
            agencyModules.save(AgencyModule.enable(agency.getId(), ModuleCode.TRAVEL, Instant.now()));
            AgencyModule ticketing = agencyModules.save(AgencyModule.enable(agency.getId(), ModuleCode.TICKETING, Instant.now()));
            ticketing.disable(Instant.now());
            flushAndClear();

            Agency reloaded = agencies.findBySubdomain("atlas-voyages").orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(AgencyStatus.TRIAL);
            assertThat(reloaded.getIce()).isEqualTo("001234567000089");
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(reloaded.getVersion()).isNotNull();
            assertThat(subscriptions.findByAgencyIdAndStatus(reloaded.getId(), SubscriptionStatus.ACTIVE))
                    .get().extracting(Subscription::getPlanCode).isEqualTo("PRO");
            assertThat(agencyModules.findByIdAgencyIdAndEnabledTrue(reloaded.getId()))
                    .extracting(AgencyModule::getModule)
                    .containsExactly(ModuleCode.TRAVEL);
        });
    }

    @Test
    void refusesTwoAgenciesWithTheSameSubdomain() {
        assertThatThrownBy(() -> inPlatformTransaction(() -> {
            agencies.save(Agency.createCustomer("Atlas Voyages", "atlas"));
            agencies.save(Agency.createCustomer("Autre agence", "atlas"));
            flushAndClear();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesASecondActiveSubscription() {
        assertThatThrownBy(() -> inPlatformTransaction(() -> {
            Agency agency = agencies.save(Agency.createCustomer("Atlas Voyages", "atlas-2"));
            subscriptions.save(Subscription.start(agency.getId(), "STARTER", SubscriptionPeriod.MONTHLY, LocalDate.of(2026, 12, 1)));
            subscriptions.save(Subscription.start(agency.getId(), "PRO", SubscriptionPeriod.MONTHLY, LocalDate.of(2026, 12, 1)));
            flushAndClear();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Perimetre plateforme + transaction systematiquement annulee : aucun test ne laisse de donnees. */
    private void inPlatformTransaction(Runnable body) {
        TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            body.run();
        }));
    }

    private void flushAndClear() {
        agencies.flush();
        entityManager.clear();
    }
}
