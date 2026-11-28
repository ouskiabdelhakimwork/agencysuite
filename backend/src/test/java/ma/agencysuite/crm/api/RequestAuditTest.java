package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.RequestApi.request;
import static ma.agencysuite.crm.RequestApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.RequestApi;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal d'audit des demandes : une entree REQUEST_STATUS_CHANGED par changement effectif de
 * statut, details limites a from, to et lostReason (codes) ; rien pour la creation, la
 * modification, une transition idempotente ou refusee.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestAuditTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();
    private RequestApi api;
    private CrmApi customers;
    private UUID agencyId;
    private UUID atlas;

    @BeforeEach
    void createAgency() throws Exception {
        api = new RequestApi(mvc);
        customers = new CrmApi(mvc);
        agencyId = data.createAgency("reqa-" + suffix);
        atlas = customers.createOk(agency(agencyId), CrmApi.company("Atlas Pharma Test"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void eachEffectiveStatusChangeIsAuditedWithCodesOnly() throws Exception {
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));
        Map<String, Object> body = with(with(with(request(atlas, "WHATSAPP"), "destination", "Istanbul"),
                "description", "Seminaire Atlas"), "budget", Map.of("amount", "25000.00"));
        UUID id = api.createOk(agency(agencyId), body);

        api.moveTo(agency(agencyId), id, "QUOTING");
        api.moveTo(agency(agencyId), id, "QUOTE_SENT");
        api.changeStatus(agency(agencyId), id, "LOST", "PRICE", "Concurrent Voyages Test moins cher")
                .andExpect(status().isOk());
        api.moveTo(agency(agencyId), id, "NEW");

        List<Row> entries = requestEntries();
        assertThat(entries).hasSize(4);
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.action()).isEqualTo("REQUEST_STATUS_CHANGED");
            assertThat(row.agencyId()).isEqualTo(agencyId);
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(manager);
            assertThat(row.entityType()).isEqualTo("REQUEST");
            assertThat(row.entityId()).isEqualTo(id.toString());
            assertThat(row.details()).doesNotContain("Istanbul").doesNotContain("Seminaire")
                    .doesNotContain("Concurrent").doesNotContain("25000").doesNotContain("Atlas");
        });
        assertThat(entries).extracting(row -> details(row))
                .containsExactlyInAnyOrder(
                        Map.of("from", "NEW", "to", "QUOTING"),
                        Map.of("from", "QUOTING", "to", "QUOTE_SENT"),
                        Map.of("from", "QUOTE_SENT", "to", "LOST", "lostReason", "PRICE"),
                        Map.of("from", "LOST", "to", "NEW"));
    }

    @Test
    void noEntryForCreationUpdateIdempotentOrRefusedChanges() throws Exception {
        UUID id = api.createOk(agency(agencyId), request(atlas, "PHONE"));
        String owner = api.read(agency(agencyId), id, "$.ownerUserId");
        api.update(agency(agencyId), id, 0, with(request(atlas, "EMAIL"), "ownerUserId", owner))
                .andExpect(status().isOk());
        api.changeStatus(agency(agencyId), id, "NEW", null, null).andExpect(status().isOk());
        customers.expectProblem(api.changeStatus(agency(agencyId), id, "WON", null, null), 409,
                "invalid-status-transition");
        customers.expectProblem(api.changeStatus(agency(agencyId), id, "LOST", null, null), 400,
                "validation-failed");
        customers.expectProblem(api.changeStatus(agency(agencyId), id, 0, "QUOTING", null, null), 409,
                "stale-version");

        assertThat(requestEntries()).isEmpty();
    }

    private List<Row> requestEntries() {
        return audit.entries(agencyId).stream().filter(row -> "REQUEST".equals(row.entityType())).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Row row) {
        return JSON.readValue(row.details(), Map.class);
    }
}
