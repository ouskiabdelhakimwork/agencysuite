package ma.agencysuite.iam;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Garde @RequiresPermission, sur le controleur de test {@link PermissionProbeController}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequiresPermissionTest {

    private static final String PROBES = "/api/v1/test/permission-probes";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;
    private String sales;

    @BeforeEach
    void createAgencyWithASalesUser() {
        agencyId = data.createAgency("perm-" + suffix);
        sales = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void managerHasThePermission() throws Exception {
        mvc.perform(get(PROBES).with(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(content().string("audit"));
    }

    @Test
    void userWithoutThePermissionIsRefused() throws Exception {
        mvc.perform(get(PROBES).with(agencyUser(agencyId, sales)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:permission-denied"))
                .andExpect(jsonPath("$.permission").value("audit.read"))
                .andExpect(jsonPath("$.title").value("Permission insuffisante"));
    }

    @Test
    void refusalIsTranslated() throws Exception {
        mvc.perform(get(PROBES).with(agencyUser(agencyId, sales)).header(HttpHeaders.ACCEPT_LANGUAGE, "ar-MA"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("صلاحية غير كافية"));
        mvc.perform(get(PROBES).with(agencyUser(agencyId, sales)).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.title").value("Insufficient permission"))
                .andExpect(jsonPath("$.detail").value(
                        "Your role does not grant the audit.read permission required for this operation."));
    }

    @Test
    void methodAnnotationWinsOverTheClassAnnotation() throws Exception {
        mvc.perform(get(PROBES + "/identity").with(agencyUser(agencyId, sales)))
                .andExpect(status().isOk())
                .andExpect(content().string("identity"));
    }

    @Test
    void moduleNotEnabledIsAnsweredBeforeThePermission() throws Exception {
        mvc.perform(get(PROBES + "/travel").with(agencyUser(agencyId, sales)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:module-not-enabled"))
                .andExpect(jsonPath("$.module").value("TRAVEL"));

        data.enable(agencyId, ModuleCode.TRAVEL);

        mvc.perform(get(PROBES + "/travel").with(agencyUser(agencyId, sales)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:permission-denied"));
        mvc.perform(get(PROBES + "/travel").with(agency(agencyId))).andExpect(status().isOk());
    }
}
