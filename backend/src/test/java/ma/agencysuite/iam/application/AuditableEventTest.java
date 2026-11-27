package ma.agencysuite.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.audit.AuditableEvent;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantProbe;
import ma.agencysuite.shared.tenant.TenantProbeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit depuis un module metier par l'evenement generique {@link AuditableEvent} : l'entree est
 * ecrite dans la transaction de l'action, et un code d'action inconnu annule l'action.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditableEventTest {

    @Autowired
    private PlatformTestData data;
    @Autowired
    private AuditTestData audit;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private TenantProbeRepository probes;
    @Autowired
    private TransactionTemplate transaction;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("auditable-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aKnownActionIsRecordedWithTheAction() {
        AgencyId agency = new AgencyId(agencyId);
        String entityId = UUID.randomUUID().toString();

        TenantContext.runAsAgency(agency, () -> transaction.executeWithoutResult(status -> {
            probes.save(new TenantProbe("action"));
            events.publishEvent(new AuditableEvent(agency, "CUSTOMER_ARCHIVED", "CUSTOMER", entityId,
                    Map.of("type", "COMPANY")));
        }));

        assertThat(countProbes(agency)).isEqualTo(1);
        assertThat(audit.entries(agencyId))
                .filteredOn(row -> row.action().equals("CUSTOMER_ARCHIVED"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.entityType()).isEqualTo("CUSTOMER");
                    assertThat(row.entityId()).isEqualTo(entityId);
                    assertThat(row.details()).contains("\"type\"").contains("COMPANY");
                });
    }

    @Test
    void anUnknownActionRollsBackTheAction() {
        AgencyId agency = new AgencyId(agencyId);
        int entriesBefore = audit.entries(agencyId).size();

        assertThatThrownBy(() -> TenantContext.runAsAgency(agency, () -> transaction.executeWithoutResult(status -> {
            probes.save(new TenantProbe("action"));
            events.publishEvent(new AuditableEvent(agency, "NOT_AN_ACTION", "CUSTOMER", "x", Map.of()));
        }))).isInstanceOf(IllegalArgumentException.class);

        assertThat(countProbes(agency)).isZero();
        assertThat(audit.entries(agencyId)).hasSize(entriesBefore)
                .extracting(Row::action).doesNotContain("NOT_AN_ACTION");
    }

    @Test
    void anAuditableEventRequiresAnAgencyAnActionAndAnEntityType() {
        AgencyId agency = new AgencyId(agencyId);

        assertThatThrownBy(() -> new AuditableEvent(null, "CUSTOMER_ARCHIVED", "CUSTOMER", "x", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditableEvent(agency, null, "CUSTOMER", "x", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditableEvent(agency, "CUSTOMER_ARCHIVED", null, "x", Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private long countProbes(AgencyId agency) {
        Long count = TenantContext.callAsAgency(agency, () -> transaction.execute(status -> probes.count()));
        return count == null ? 0 : count;
    }
}
