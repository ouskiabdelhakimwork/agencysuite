package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static ma.agencysuite.crm.TravelerApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.TravelerApi;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Archivage et restauration des voyageurs (idempotents, audites sans donnee personnelle). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerArchiveTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new TravelerApi(mvc);
        agencyId = data.createAgency("tarch-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void anArchivedTravelerLeavesTheDefaultListButStaysReadable() throws Exception {
        UUID archived = api.createOk(agency(agencyId), traveler("Archive", "Test"));
        api.createOk(agency(agencyId), traveler("Actif", "Test"));

        api.archive(agency(agencyId), archived)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        api.list(agency(agencyId), "").andExpect(jsonPath("$.items[*].lastName").value(contains("Actif")));
        api.list(agency(agencyId), "archived=true").andExpect(jsonPath("$.items[*].lastName").value(contains("Archive")));
        api.list(agency(agencyId), "q=archive").andExpect(jsonPath("$.totalElements").value(0));
        api.get(agency(agencyId), archived).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void archiveAndRestoreAreIdempotentAndAuditedOnceWithEmptyDetails() throws Exception {
        var body = with(traveler("El Fassi", "Zineb"), "birthDate", "1990-05-14");
        body.put("nationality", "MA");
        body.put("email", "zineb@example.test");
        UUID id = api.createOk(agency(agencyId), body);
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));

        api.archive(agency(agencyId), id).andExpect(status().isOk());
        api.archive(agency(agencyId), id).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        api.restore(agency(agencyId), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").value(nullValue()));
        api.restore(agency(agencyId), id).andExpect(status().isOk());

        List<Row> entries = audit.entries(agencyId).stream()
                .filter(row -> row.action().startsWith("TRAVELER_"))
                .toList();
        assertThat(entries).extracting(Row::action).containsExactly("TRAVELER_ARCHIVED", "TRAVELER_RESTORED");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.agencyId()).isEqualTo(agencyId);
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(manager);
            assertThat(row.entityType()).isEqualTo("TRAVELER");
            assertThat(row.entityId()).isEqualTo(id.toString());
            assertThat(row.details()).isEqualTo("{}");
        });
    }

    @Test
    void creatingOrUpdatingATravelerIsNotAudited() throws Exception {
        UUID id = api.createOk(agency(agencyId), traveler("Bennani", "Salma"));
        api.update(agency(agencyId), id, 0, traveler("Bennani", "Salma Test")).andExpect(status().isOk());

        assertThat(audit.entries(agencyId)).extracting(Row::action).noneMatch(action -> action.startsWith("TRAVELER_"));
    }

    @Test
    void anArchivedTravelerCanStillBeUpdated() throws Exception {
        UUID id = api.createOk(agency(agencyId), traveler("Bennani", "Salma"));
        api.archive(agency(agencyId), id).andExpect(status().isOk());

        long version = api.version(agency(agencyId), id);
        api.update(agency(agencyId), id, version, traveler("Bennani", "Salma Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void thereIsNoDeleteEndpoint() throws Exception {
        UUID id = api.createOk(agency(agencyId), traveler("Bennani", "Salma"));

        mvc.perform(MockMvcRequestBuilders.delete(TravelerApi.TRAVELERS + "/" + id).with(agency(agencyId)))
                .andExpect(status().is4xxClientError());
        api.get(agency(agencyId), id).andExpect(status().isOk());
    }
}
