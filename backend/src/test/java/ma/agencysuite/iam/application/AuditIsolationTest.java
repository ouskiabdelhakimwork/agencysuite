package ma.agencysuite.iam.application;

import static ma.agencysuite.TestTokens.agency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Isolation du journal d'audit entre agences, et ajout seul. AuditEntry n'etant pas filtree par
 * Hibernate (exception multi-tenant), la RLS et le filtre explicite de la lecture sont les barrieres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditIsolationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private AuditTestData audit;
    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgenciesWithEntries() {
        agencyA = data.createAgency("aiso-a-" + suffix, ModuleCode.TRAVEL);
        agencyB = data.createAgency("aiso-b-" + suffix, ModuleCode.MICE, ModuleCode.VISA);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void agencyReadsOnlyItsOwnEntriesThroughTheApi() throws Exception {
        mvc.perform(get("/api/v1/audit-events").with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[*].entityId", Matchers.contains("TRAVEL")));
        mvc.perform(get("/api/v1/audit-events").with(agency(agencyB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].entityId", Matchers.containsInAnyOrder("MICE", "VISA")));
    }

    @Test
    void databaseHidesTheEntriesOfAnotherAgencyEvenInDirectSql() {
        Number seenByA = asAgency(agencyA, () -> (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM audit_log WHERE agency_id = :b")
                .setParameter("b", agencyB).getSingleResult());
        Number allSeenByA = asAgency(agencyA, () -> (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM audit_log").getSingleResult());

        assertThat(seenByA.longValue()).isZero();
        assertThat(allSeenByA.longValue()).isEqualTo(1);
    }

    @Test
    void databaseRefusesAnEntryForAnotherAgency() {
        assertThatThrownBy(() -> asAgency(agencyA, () -> entityManager.createNativeQuery("""
                INSERT INTO audit_log (id, agency_id, actor_type, action, entity_type, occurred_at)
                VALUES (:id, :b, 'SYSTEM', 'MODULE_ENABLED', 'MODULE', now())
                """).setParameter("id", UUID.randomUUID()).setParameter("b", agencyB).executeUpdate()))
                .rootCause()
                .hasMessageContaining("row-level security");
        assertThatThrownBy(() -> asAgency(agencyA, () -> {
            auditTrail.record(new AgencyId(agencyB), AuditAction.MODULE_ENABLED, "MODULE", "TRAVEL",
                    Map.of("module", "TRAVEL"));
            entityManager.flush();
            return null;
        }))
                .rootCause()
                .hasMessageContaining("row-level security");

        assertThat(audit.entries(agencyB)).hasSize(2);
    }

    @Test
    void applicationRoleCanNeitherUpdateNorDeleteAnEntry() {
        assertThatThrownBy(() -> asPlatform(() -> entityManager
                .createNativeQuery("UPDATE audit_log SET action = 'TAMPERED' WHERE agency_id = :a")
                .setParameter("a", agencyA).executeUpdate()))
                .rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asPlatform(() -> entityManager
                .createNativeQuery("DELETE FROM audit_log WHERE agency_id = :a")
                .setParameter("a", agencyA).executeUpdate()))
                .rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asAgency(agencyA, () -> entityManager
                .createNativeQuery("DELETE FROM audit_log").executeUpdate()))
                .rootCause()
                .hasMessageContaining("permission denied");

        assertThat(audit.entries(agencyA)).extracting(AuditTestData.Row::action).containsExactly("MODULE_ENABLED");
    }

    private <T> T asAgency(UUID agencyId, Supplier<T> work) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(status -> work.get()));
    }

    private <T> T asPlatform(Supplier<T> work) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> work.get()));
    }
}
