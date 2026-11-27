package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.CrmApi.company;
import static ma.agencysuite.crm.CrmApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
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

/** Archivage et restauration (idempotents, audites), sans suppression definitive. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerArchiveTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new CrmApi(mvc);
        agencyId = data.createAgency("archive-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void anArchivedCustomerLeavesTheDefaultListButStaysReadable() throws Exception {
        UUID archived = api.createOk(agency(agencyId), company("Archivee Test"));
        api.createOk(agency(agencyId), company("Active Test"));

        api.archive(agency(agencyId), archived)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        api.list(agency(agencyId), "").andExpect(jsonPath("$.items[*].name").value(contains("Active Test")));
        api.list(agency(agencyId), "archived=true")
                .andExpect(jsonPath("$.items[*].name").value(contains("Archivee Test")))
                .andExpect(jsonPath("$.items[0].archived").value(true));
        api.list(agency(agencyId), "q=archivee").andExpect(jsonPath("$.totalElements").value(0));
        api.get(agency(agencyId), archived).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archiveAndRestoreAreIdempotentAndAuditedOnce() throws Exception {
        UUID id = api.createOk(agency(agencyId), with(company("Atlas Pharma Test"), "email", "atlas@example.test"));
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));

        api.archive(agency(agencyId), id).andExpect(status().isOk());
        api.archive(agency(agencyId), id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        api.restore(agency(agencyId), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));
        api.restore(agency(agencyId), id).andExpect(status().isOk());

        List<Row> entries = audit.entries(agencyId).stream()
                .filter(row -> row.action().startsWith("CUSTOMER_"))
                .toList();
        assertThat(entries).extracting(Row::action).containsExactly("CUSTOMER_ARCHIVED", "CUSTOMER_RESTORED");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.agencyId()).isEqualTo(agencyId);
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(manager);
            assertThat(row.entityType()).isEqualTo("CUSTOMER");
            assertThat(row.entityId()).isEqualTo(id.toString());
            assertThat(row.details()).contains("COMPANY")
                    .doesNotContain("Atlas").doesNotContain("atlas@example.test");
        });
        api.list(agency(agencyId), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void creatingOrUpdatingACustomerIsNotAudited() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));
        api.update(agency(agencyId), id, 0, company("Societe Modifiee Test")).andExpect(status().isOk());

        assertThat(audit.entries(agencyId)).extracting(Row::action).noneMatch(action -> action.startsWith("CUSTOMER_"));
    }

    @Test
    void anArchivedCustomerCanStillBeUpdated() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));
        api.archive(agency(agencyId), id).andExpect(status().isOk());

        long version = api.version(agency(agencyId), id);
        api.update(agency(agencyId), id, version, company("Societe Modifiee Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void thereIsNoDeleteEndpoint() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));

        api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(CrmApi.CUSTOMERS + "/" + id).with(agency(agencyId)))
                .andExpect(status().is4xxClientError());
        api.get(agency(agencyId), id).andExpect(status().isOk());
    }
}
