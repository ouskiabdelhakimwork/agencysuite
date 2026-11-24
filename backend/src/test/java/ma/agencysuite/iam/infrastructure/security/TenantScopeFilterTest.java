package ma.agencysuite.iam.infrastructure.security;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyWithOrganizations;
import static ma.agencysuite.TestTokens.agencyWithoutOrganization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Ouverture du perimetre agence a partir du claim organization du jeton. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantScopeFilterTest {

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
        agencyA = data.createAgency("scope-a-" + suffix, ModuleCode.TRAVEL);
        agencyB = data.createAgency("scope-b-" + suffix, ModuleCode.MICE, ModuleCode.GIFTBOX);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void validAgencyTokenOpensTheScopeOfThatAgencyOnly() throws Exception {
        mvc.perform(get(ME_MODULES).with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("TRAVEL")));
        mvc.perform(get(ME_MODULES).with(agency(agencyB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", Matchers.contains("GIFTBOX", "MICE")));
    }

    @Test
    void noOrganizationIsAgencyScopeRequired() throws Exception {
        mvc.perform(get(ME_MODULES).with(agencyWithoutOrganization()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-scope-required"));
        mvc.perform(get(ME_MODULES).with(agencyWithOrganizations(List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-scope-required"));
    }

    @Test
    void twoOrganizationsAreRefused() throws Exception {
        mvc.perform(get(ME_MODULES).with(agencyWithOrganizations(List.of(agencyA.toString(), agencyB.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
    }

    @Test
    void aliasThatIsNotAUuidIsRefused() throws Exception {
        for (String alias : List.of("atlas-voyages", "1-2-3-4-5", agencyA + "x", "")) {
            mvc.perform(get(ME_MODULES).with(agencyWithOrganizations(List.of(alias))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        }
    }

    @Test
    void unknownAgencyIsUnavailable() throws Exception {
        mvc.perform(get(ME_MODULES).with(agency(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-unavailable"))
                .andExpect(jsonPath("$.title").value("Agence indisponible"));
    }

    @Test
    void suspendedAgencyGetsExactlyTheSameAnswerAsAnUnknownOne() throws Exception {
        data.suspend(agencyA);

        String suspended = mvc.perform(get(ME_MODULES).with(agency(agencyA)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknown = mvc.perform(get(ME_MODULES).with(agency(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(suspended).contains("urn:agencysuite:problem:agency-unavailable").isEqualTo(unknown);
        // L'autre agence n'est pas affectee
        mvc.perform(get(ME_MODULES).with(agency(agencyB))).andExpect(status().isOk());
    }
}
