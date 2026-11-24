package ma.agencysuite.platform.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyWithoutOrganization;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/me/modules : modules actifs de l'agence courante uniquement (isolation entre agences).
 * Le perimetre agence est ouvert par la chaine de securite a partir d'un jeton simule (TestTokens).
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
        mvc.perform(get(ME_MODULES).with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("TRAVEL", "GIFTBOX")));
    }

    @Test
    void neverReturnsTheModulesOfAnotherAgency() throws Exception {
        mvc.perform(get(ME_MODULES).with(agency(agencyB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("MICE")));
    }

    @Test
    void unknownAgencyIsRefusedBeforeReachingTheModules() throws Exception {
        // Etape 3 : une agence inconnue n'ouvre plus de perimetre (agency-unavailable, voir TenantScopeFilterTest).
        mvc.perform(get(ME_MODULES).with(agency(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-unavailable"));
    }

    @Test
    void isForbiddenWithoutAnAgencyScope() throws Exception {
        mvc.perform(get(ME_MODULES).with(agencyWithoutOrganization()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-scope-required"));
    }
}
