package ma.agencysuite.platform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.domain.Agency;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconnaissance de la contrainte uk_agency_subdomain dans une vraie erreur PostgreSQL
 * (nom extrait par Hibernate) ; les autres violations ne sont pas confondues avec elle.
 * Transactions systematiquement annulees : aucune donnee ne reste.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgencyConstraintsTest {

    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private TransactionTemplate transaction;

    @Test
    void recognizesTheSubdomainUniqueConstraint() {
        DataIntegrityViolationException error = violation(() -> {
            agencies.saveAndFlush(Agency.createCustomer("Agence 1", "uk-test"));
            agencies.saveAndFlush(Agency.createCustomer("Agence 2", "uk-test"));
        });

        assertThat(AgencyConstraints.isSubdomainTaken(error)).isTrue();
    }

    @Test
    void doesNotMistakeAnotherConstraintForTheSubdomainOne() {
        DataIntegrityViolationException error = violation(() -> {
            Agency agency = agencies.saveAndFlush(Agency.createCustomer("Agence", "uk-other"));
            subscriptions.saveAndFlush(Subscription.start(agency.getId(), "PRO", SubscriptionPeriod.MONTHLY,
                    LocalDate.of(2026, 12, 1)));
            subscriptions.saveAndFlush(Subscription.start(agency.getId(), "STARTER", SubscriptionPeriod.MONTHLY,
                    LocalDate.of(2026, 12, 1)));
        });

        assertThat(AgencyConstraints.isSubdomainTaken(error)).isFalse();
    }

    private DataIntegrityViolationException violation(Runnable body) {
        DataIntegrityViolationException error = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    body.run();
                })));
        assertThat(error).as("violation de contrainte attendue").isNotNull();
        return error;
    }
}
