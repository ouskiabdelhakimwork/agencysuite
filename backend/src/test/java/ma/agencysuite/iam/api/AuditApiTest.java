package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.TestTokens.platform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
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
