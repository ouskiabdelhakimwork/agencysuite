package ma.agencysuite.iam.application;

import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Entrees ecrites pour les actions du back-office (creation d'agence, activation de modules). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditTrailTest {

    /** platform_user.id du membre ADMIN insere par V1001 (TestTokens.PLATFORM_ADMIN_SUB). */
    static final UUID PLATFORM_ADMIN_ID = UUID.fromString("7e57ad41-0000-7000-8000-000000000001");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void agencyCreationRecordsAgencyCreatedAndOneModuleEnabledPerModule() throws Exception {
        UUID agencyId = createAgency(mvc, "audit-" + suffix);

        List<Row> entries = audit.entries(agencyId);

        assertThat(entries).extracting(Row::action)
                .containsExactly("AGENCY_CREATED", "MODULE_ENABLED", "MODULE_ENABLED");
        Row created = entries.getFirst();
        assertThat(created.entityType()).isEqualTo("AGENCY");
        assertThat(created.entityId()).isEqualTo(agencyId.toString());
        assertThat(created.details()).isEqualTo("{\"plan\": \"PRO\", \"modules\": [\"TICKETING\", \"TRAVEL\"]}");
        assertThat(entries.subList(1, 3)).extracting(Row::entityType).containsOnly("MODULE");
        assertThat(entries.subList(1, 3)).extracting(Row::entityId).containsExactlyInAnyOrder("TICKETING", "TRAVEL");
        assertThat(entries.subList(1, 3)).extracting(Row::details)
                .containsExactlyInAnyOrder("{\"module\": \"TICKETING\"}", "{\"module\": \"TRAVEL\"}");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.actorType()).isEqualTo("PLATFORM_USER");
            assertThat(row.actorId()).isEqualTo(PLATFORM_ADMIN_ID);
            assertThat(row.ip()).isEqualTo("127.0.0.1");
            assertThat(row.occurredAt()).isNotNull();
        });
    }

    @Test
    void disablingAModuleRecordsModuleDisabled() throws Exception {
        UUID agencyId = createAgency(mvc, "audit-off-" + suffix);

        mvc.perform(delete(module(agencyId, "TRAVEL")).with(platform())).andExpect(status().isNoContent());

        Row last = audit.entries(agencyId).getLast();
        assertThat(last.action()).isEqualTo("MODULE_DISABLED");
        assertThat(last.entityType()).isEqualTo("MODULE");
        assertThat(last.entityId()).isEqualTo("TRAVEL");
        assertThat(last.details()).isEqualTo("{\"module\": \"TRAVEL\"}");
        assertThat(last.actorType()).isEqualTo("PLATFORM_USER");
        assertThat(last.actorId()).isEqualTo(PLATFORM_ADMIN_ID);
    }

    @Test
    void enablingAnAlreadyEnabledModuleRecordsNothing() throws Exception {
        UUID agencyId = createAgency(mvc, "audit-idem-" + suffix);
        int before = audit.entries(agencyId).size();

        mvc.perform(put(module(agencyId, "TRAVEL")).with(platform())).andExpect(status().isNoContent());
        mvc.perform(delete(module(agencyId, "MICE")).with(platform())).andExpect(status().isNoContent());

        assertThat(audit.entries(agencyId)).hasSize(before);
    }

    @Test
    void anActionWithoutHttpRequestNorActorIsRecordedAsSystem() throws Exception {
        // Fil separe, comme une tache de fond : le fil du test porte une fausse requete HTTP
        // (RequestContextHolder rempli par ServletTestExecutionListener, adresse 127.0.0.1).
        UUID agencyId;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            agencyId = executor.submit(() -> data.createAgency("audit-sys-" + suffix, ModuleCode.VISA)).get();
        }

        Row entry = audit.entries(agencyId).getFirst();
        assertThat(entry.action()).isEqualTo("MODULE_ENABLED");
        assertThat(entry.actorType()).isEqualTo("SYSTEM");
        assertThat(entry.actorId()).isNull();
        assertThat(entry.ip()).isNull();
    }

    /** Cree une agence par le back-office (modules TICKETING et TRAVEL) ; renvoie son identifiant. */
    static UUID createAgency(MockMvc mvc, String subdomain) throws Exception {
        String body = mvc.perform(post("/api/v1/backoffice/agencies").with(platform())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Agence audit", "subdomain": "%s", "planCode": "PRO", "period": "MONTHLY",
                                 "startDate": "2026-12-01", "modules": ["TICKETING", "TRAVEL"]}
                                """.formatted(subdomain)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    static String module(UUID agencyId, String code) {
        return "/api/v1/backoffice/agencies/" + agencyId + "/modules/" + code;
    }
}
