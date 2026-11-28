package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.AuditEntry;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.AuditEntryRepository;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.security.Actor;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** GET /api/v1/audit-events : journal de l'agence, permission audit.read. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditApiTest {

    private static final String EVENTS = "/api/v1/audit-events";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditEntryRepository auditEntries;
    @Autowired
    private TransactionTemplate transaction;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgencyWithThreeEntries() {
        agencyId = data.createAgency("aapi-" + suffix, ModuleCode.TRAVEL, ModuleCode.MICE);
        data.disable(agencyId, ModuleCode.TRAVEL);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void managerReadsItsAgencyJournalMostRecentFirstWithoutIp() throws Exception {
        mvc.perform(get(EVENTS).with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action",
                        Matchers.contains("MODULE_DISABLED", "MODULE_ENABLED", "MODULE_ENABLED")))
                .andExpect(jsonPath("$.items[*].entityId", Matchers.contains("TRAVEL", "MICE", "TRAVEL")))
                .andExpect(jsonPath("$.items[0].entityType").value("MODULE"))
                .andExpect(jsonPath("$.items[0].actorType").value("SYSTEM"))
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.items[0].details.module").value("TRAVEL"))
                .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.items[*].ip").doesNotExist())
                .andExpect(jsonPath("$.items[*].agencyId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void paginates() throws Exception {
        mvc.perform(get(EVENTS).param("page", "0").param("size", "2").with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action", Matchers.contains("MODULE_DISABLED", "MODULE_ENABLED")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(EVENTS).param("page", "1").param("size", "2").with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[*].entityId", Matchers.contains("TRAVEL")));
    }

    /**
     * Entrees de meme occurred_at (ecrites dans la meme transaction) : l'identifiant departage, l'ordre
     * est total, donc en pages de taille 1 chaque entree apparait une et une seule fois, et toujours a
     * la meme place.
     */
    @Test
    void paginationIsStableWhenSeveralEntriesShareTheSameInstant() throws Exception {
        Instant sameInstant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<String> tied = List.of("tie-1", "tie-2", "tie-3", "tie-4", "tie-5");
        AgencyId agency = new AgencyId(agencyId);
        TenantContext.runAsAgency(agency, () -> transaction.executeWithoutResult(status -> {
            for (String entityId : tied) {
                auditEntries.save(new AuditEntry(agency, Actor.SYSTEM, AuditAction.CUSTOMER_ARCHIVED, "CUSTOMER",
                        entityId, Map.of("type", "COMPANY"), null, sameInstant));
            }
        }));
        int total = 3 + tied.size();

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < total; page++) {
            String first = pageOfOne(page);
            assertThat(pageOfOne(page)).as("page %d relue", page).isEqualTo(first);
            seen.add(first);
        }

        assertThat(seen).doesNotHaveDuplicates().hasSize(total);
        assertThat(seen).filteredOn(entry -> entry.startsWith("CUSTOMER_ARCHIVED:"))
                .extracting(entry -> entry.substring("CUSTOMER_ARCHIVED:".length()))
                .containsExactlyInAnyOrderElementsOf(tied);
        mvc.perform(get(EVENTS).param("page", String.valueOf(total)).param("size", "1").with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(total));
    }

    /** Action et entite de l'unique entree de la page (taille 1). */
    private String pageOfOne(int page) throws Exception {
        String body = mvc.perform(get(EVENTS).param("page", String.valueOf(page)).param("size", "1")
                        .with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[0].action") + ":" + JsonPath.read(body, "$.items[0].entityId");
    }

    @Test
    void sizeIsLimitedTo200() throws Exception {
        mvc.perform(get(EVENTS).param("size", "200").with(agency(agencyId))).andExpect(status().isOk());
        for (String size : new String[] {"201", "0", "-1"}) {
            mvc.perform(get(EVENTS).param("size", size).with(agency(agencyId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:validation-failed"))
                    .andExpect(jsonPath("$.title").value("Données invalides"));
        }
        mvc.perform(get(EVENTS).param("page", "-1").with(agency(agencyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:validation-failed"));
    }

    @Test
    void salesIsRefusedWithPermissionDenied() throws Exception {
        String sales = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");

        mvc.perform(get(EVENTS).with(agencyUser(agencyId, sales)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:permission-denied"))
                .andExpect(jsonPath("$.permission").value("audit.read"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void platformTokenAndAnonymousAreRefused() throws Exception {
        mvc.perform(get(EVENTS).with(platform())).andExpect(status().isForbidden());
        mvc.perform(get(EVENTS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
