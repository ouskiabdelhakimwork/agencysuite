package ma.agencysuite.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.TestAgencyScopeFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/me/modules : modules actifs de l'agence courante uniquement (isolation entre agences).
 * Le perimetre agence est ouvert par le filtre de test (en-tete X-Test-Agency).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeModulesApiTest {

    private static final String ME_MODULES = "/api/v1/me/modules";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createTwoAgencies() {
        agencyA = data.createAgency("me-a-" + suffix, ModuleCode.TRAVEL, ModuleCode.GIFTBOX, ModuleCode.TICKETING);
        data.disable(agencyA, ModuleCode.TICKETING);
        agencyB = data.createAgency("me-b-" + suffix, ModuleCode.MICE);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void returnsOnlyTheActiveModulesOfTheCurrentAgency() throws Exception {
        mvc.perform(get(ME_MODULES).header(TestAgencyScopeFilter.HEADER, agencyA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("TRAVEL", "GIFTBOX")));
    }

    @Test
    void neverReturnsTheModulesOfAnotherAgency() throws Exception {
        mvc.perform(get(ME_MODULES).header(TestAgencyScopeFilter.HEADER, agencyB.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("MICE")));
    }

    @Test
    void unknownAgencyHasNoModules() throws Exception {
        mvc.perform(get(ME_MODULES).header(TestAgencyScopeFilter.HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules").isEmpty());
    }

    @Test
    void isForbiddenWithoutAnAgencyScope() throws Exception {
        mvc.perform(get(ME_MODULES))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-scope-required"));
    }
}
