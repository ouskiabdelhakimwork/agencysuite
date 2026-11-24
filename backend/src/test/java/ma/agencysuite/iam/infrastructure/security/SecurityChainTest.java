package ma.agencysuite.iam.infrastructure.security;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Les trois chaines de securite : qui passe, qui est refuse, et sous quel format. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityChainTest {

    private static final String ME_MODULES = "/api/v1/me/modules";
    private static final String BACKOFFICE_AGENCIES = "/api/v1/backoffice/agencies";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void agencyApiWithoutTokenIs401ProblemJsonWithBearerChallenge() throws Exception {
        mvc.perform(get(ME_MODULES))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Authentification requise"));
    }

    @Test
    void unauthenticatedProblemIsTranslated() throws Exception {
        mvc.perform(get(ME_MODULES).header(HttpHeaders.ACCEPT_LANGUAGE, "ar-MA"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("تسجيل الدخول مطلوب"));
        mvc.perform(get(ME_MODULES).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void backofficeWithoutTokenIs401() throws Exception {
        mvc.perform(get(BACKOFFICE_AGENCIES + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:unauthenticated"));
        mvc.perform(post(BACKOFFICE_AGENCIES).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIs401WithoutTheReason() throws Exception {
        MvcResult result = mvc.perform(get(ME_MODULES).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                // Pas de error="invalid_token" ni de description : aucune raison du rejet
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:unauthenticated"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContainIgnoringCase("jwt")
                .doesNotContainIgnoringCase("malformed")
                .doesNotContain("Exception");
    }

    @Test
    void platformInfoIsPublic() throws Exception {
        mvc.perform(get("/api/v1/platform/info")).andExpect(status().isOk());
    }

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void otherActuatorEndpointsAreRefused() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        mvc.perform(get("/actuator/env").with(platform())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator")).andExpect(status().isForbidden());
    }

    @Test
    void agencyTokenIsNeverAcceptedOnTheBackoffice() throws Exception {
        UUID agencyId = data.createAgency("sec-bo-" + suffix, ModuleCode.TRAVEL);

        mvc.perform(get(BACKOFFICE_AGENCIES + "/" + agencyId).with(agency(agencyId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        mvc.perform(post(BACKOFFICE_AGENCIES).with(agency(agencyId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTokenIsRefusedOnTheAgencyApi() throws Exception {
        mvc.perform(get(ME_MODULES).with(platform()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
    }

    @Test
    void platformTokenOpensTheBackoffice() throws Exception {
        UUID agencyId = data.createAgency("sec-ok-" + suffix);

        mvc.perform(get(BACKOFFICE_AGENCIES + "/" + agencyId).with(platform()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agencyId.toString()));
    }
}
