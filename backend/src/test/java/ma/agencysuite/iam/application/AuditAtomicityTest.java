package ma.agencysuite.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.platform.ModuleEnabled;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.platform.application.ModuleActivation;
import ma.agencysuite.shared.module.ModuleCode;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** L'entree d'audit et l'action sont enregistrees ensemble, ou aucune. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditAtomicityTest {

    @Autowired
    private PlatformTestData data;
    @Autowired
    private AuditTestData audit;
    @Autowired
    private ModuleActivation moduleActivation;
    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private TenantProbeRepository probes;
    @Autowired
    private TransactionTemplate transaction;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        agencyA = data.createAgency("atom-a-" + suffix);
        agencyB = data.createAgency("atom-b-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aRolledBackActionLeavesNoEntry() {
        assertThatThrownBy(() -> TenantContext.runAsPlatform(() -> transaction.executeWithoutResult(status -> {
            moduleActivation.enable(agencyA, ModuleCode.TRAVEL);
            throw new IllegalStateException("echec apres la publication de l'evenement");
        }))).isInstanceOf(IllegalStateException.class);

        assertThat(audit.entries(agencyA)).isEmpty();
        assertThat(data.agencyModule(agencyA, ModuleCode.TRAVEL)).isEmpty();
    }

    @Test
    void aFailedAuditWriteRollsTheActionBack() {
        // Dans le perimetre de A, l'evenement vise B : la RLS refuse l'entree (WITH CHECK), et la
        // ligne metier ecrite dans la meme transaction est annulee avec elle.
        assertThatThrownBy(() -> TenantContext.runAsAgency(new AgencyId(agencyA),
                () -> transaction.executeWithoutResult(status -> {
                    probes.save(new TenantProbe("action metier"));
                    events.publishEvent(new ModuleEnabled(new AgencyId(agencyB), ModuleCode.TRAVEL, Instant.now()));
                })))
                .rootCause()
                .hasMessageContaining("row-level security");

        Long probesOfA = TenantContext.callAsAgency(new AgencyId(agencyA),
                () -> transaction.execute(status -> probes.count()));
        assertThat(probesOfA).isZero();
        assertThat(audit.entries(agencyB)).isEmpty();
    }

    @Test
    void recordingOutsideATransactionIsAProgrammingError() {
        assertThatThrownBy(() -> TenantContext.runAsPlatform(() -> auditTrail.record(new AgencyId(agencyA),
                AuditAction.MODULE_ENABLED, "MODULE", "TRAVEL", Map.of("module", "TRAVEL"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> TenantContext.runAsPlatform(() -> events.publishEvent(
                new ModuleEnabled(new AgencyId(agencyA), ModuleCode.TRAVEL, Instant.now()))))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(audit.entries(agencyA)).isEmpty();
    }
}
