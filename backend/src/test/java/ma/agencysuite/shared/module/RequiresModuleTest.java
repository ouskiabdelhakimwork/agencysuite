package ma.agencysuite.shared.module;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.TestTokens;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Garde @RequiresModule : un endpoint de module non souscrit renvoie 403, meme en appel direct.
 * Controleur de test {@link TravelProbeController} (module TRAVEL) ; perimetre agence ouvert
 * par la chaine de securite a partir d'un jeton simule (TestTokens).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequiresModuleTest {

    private static final String PROBES = "/api/v1/test/travel-probes";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agency;

    @BeforeEach
    void createAgencyWithoutModules() {
        agency = data.createAgency("guard-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void forbidsAModuleThatIsNotEnabled() throws Exception {
        mvc.perform(asAgency(get(PROBES), agency))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:module-not-enabled"))
                .andExpect(jsonPath("$.module").value("TRAVEL"))
                .andExpect(jsonPath("$.title").value("Module non activé"));
    }

    @Test
    void translatesTheRefusalIntoArabic() throws Exception {
        mvc.perform(asAgency(get(PROBES), agency).header(HttpHeaders.ACCEPT_LANGUAGE, "ar-MA"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("الوحدة غير مفعّلة"))
                .andExpect(jsonPath("$.detail").value("الوحدة TRAVEL غير مفعّلة لوكالتكم."));
    }

    @Test
    void allowsAModuleThatIsEnabled() throws Exception {
        data.enable(agency, ModuleCode.TRAVEL);

        mvc.perform(asAgency(get(PROBES), agency)).andExpect(status().isOk());
    }

    @Test
    void reenablingRestoresAccessToTheDataCreatedBefore() throws Exception {
        data.enable(agency, ModuleCode.TRAVEL);
        mvc.perform(asAgency(post(PROBES).contentType(MediaType.TEXT_PLAIN).content("circuit-sud"), agency))
                .andExpect(status().isCreated());

        data.disable(agency, ModuleCode.TRAVEL);
        mvc.perform(asAgency(get(PROBES), agency)).andExpect(status().isForbidden());
        mvc.perform(asAgency(post(PROBES).contentType(MediaType.TEXT_PLAIN).content("interdit"), agency))
                .andExpect(status().isForbidden());

        data.enable(agency, ModuleCode.TRAVEL);
        mvc.perform(asAgency(get(PROBES), agency))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.contains("circuit-sud")));
    }

    @Test
    void moduleEnabledForAnotherAgencyDoesNotOpenAccess() throws Exception {
        UUID other = data.createAgency("guard-other-" + suffix, ModuleCode.TRAVEL);

        mvc.perform(asAgency(get(PROBES), other)).andExpect(status().isOk());
        mvc.perform(asAgency(get(PROBES), agency)).andExpect(status().isForbidden());
    }

    @Test
    void forbidsACallWithoutAnAgencyScope() throws Exception {
        mvc.perform(get(PROBES).with(TestTokens.agencyWithoutOrganization()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static MockHttpServletRequestBuilder asAgency(MockHttpServletRequestBuilder request, UUID agencyId) {
        return request.with(TestTokens.agency(agencyId));
    }
}
