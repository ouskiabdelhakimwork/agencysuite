package ma.agencysuite.platform.api;

import static ma.agencysuite.TestTokens.platform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Back-office : creation et consultation d'une agence cliente (US-101). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BackofficeAgencyApiTest {

    private static final String AGENCIES = "/api/v1/backoffice/agencies";

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
    void createsAnAgencyWithItsPlanAndModulesThenReadsIt() throws Exception {
        MvcResult created = mvc.perform(create(fullRequest("atlas-" + suffix, "PRO")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.containsString(AGENCIES + "/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Atlas Voyages"))
                .andExpect(jsonPath("$.subdomain").value("atlas-" + suffix))
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.kind").value("CUSTOMER"))
                .andExpect(jsonPath("$.legalName").value("Atlas Voyages SARL"))
                .andExpect(jsonPath("$.ice").value("001234567000089"))
                .andExpect(jsonPath("$.planCode").value("PRO"))
                .andExpect(jsonPath("$.period").value("YEARLY"))
                .andExpect(jsonPath("$.startDate").value("2026-12-01"))
                .andExpect(jsonPath("$.enabledModules", Matchers.contains("TRAVEL", "TICKETING")))
                .andReturn();

        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        mvc.perform(get(location).with(platform()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subdomain").value("atlas-" + suffix))
                .andExpect(jsonPath("$.planCode").value("PRO"))
                .andExpect(jsonPath("$.enabledModules", Matchers.contains("TRAVEL", "TICKETING")));
    }

    @Test
    void createsAnAgencyWithoutModules() throws Exception {
        mvc.perform(create("""
                        {"name": "Agence minimale", "subdomain": "mini-%s", "planCode": "STARTER",
                         "period": "MONTHLY", "startDate": "2026-12-01"}
                        """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabledModules").isEmpty());
    }

    @Test
    void refusesASubdomainAlreadyTaken() throws Exception {
        mvc.perform(create(fullRequest("taken-" + suffix, "PRO"))).andExpect(status().isCreated());

        mvc.perform(create(fullRequest("taken-" + suffix, "STARTER")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:subdomain-taken"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Sous-domaine déjà utilisé"));
    }

    @Test
    void translatesTheErrorIntoArabic() throws Exception {
        mvc.perform(create(fullRequest("ar-" + suffix, "PRO"))).andExpect(status().isCreated());

        mvc.perform(create(fullRequest("ar-" + suffix, "PRO")).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:subdomain-taken"))
                .andExpect(jsonPath("$.title").value("النطاق الفرعي مستخدم بالفعل"))
                .andExpect(jsonPath("$.detail").value("هذا النطاق الفرعي مخصص بالفعل لوكالة أخرى. يرجى اختيار نطاق آخر."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-atlas", "a", "atlas_voyages", "atlas-"})
    void refusesAnInvalidSubdomain(String subdomain) throws Exception {
        mvc.perform(create(fullRequest(subdomain, "PRO")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:invalid-subdomain"))
                .andExpect(jsonPath("$.reason").value("INVALID_FORMAT"))
                // Message traduit : jamais le message interne de l'exception
                .andExpect(jsonPath("$.title").value("Sous-domaine invalide"))
                .andExpect(jsonPath("$.detail", Matchers.startsWith("Le sous-domaine doit comporter")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"www", "admin", "API"})
    void refusesAReservedSubdomain(String subdomain) throws Exception {
        mvc.perform(create(fullRequest(subdomain, "PRO")).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:invalid-subdomain"))
                .andExpect(jsonPath("$.reason").value("RESERVED"))
                .andExpect(jsonPath("$.title").value("Invalid subdomain"));
    }

    @Test
    void refusesMalformedJsonWithATranslatedGenericProblem() throws Exception {
        MvcResult result = mvc.perform(create("{\"name\": \"Atlas\", \"subdomain\": ").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:malformed-request"))
                .andExpect(jsonPath("$.title").value("طلب غير مقروء"))
                .andReturn();

        assertNoInternalDetails(result);
    }

    @Test
    void refusesAnUnknownModuleCodeInTheBodyWithAGenericProblem() throws Exception {
        MvcResult result = mvc.perform(create("""
                        {"name": "Atlas", "subdomain": "mod-%s", "planCode": "PRO", "period": "MONTHLY",
                         "startDate": "2026-12-01", "modules": ["SPACE_TRAVEL"]}
                        """.formatted(suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:malformed-request"))
                .andExpect(jsonPath("$.title").value("Requête illisible"))
                .andExpect(jsonPath("$.detail", Matchers.startsWith("Le corps de la requête")))
                .andReturn();

        assertNoInternalDetails(result);
    }

    @Test
    void returns404ForAnUnknownPlan() throws Exception {
        mvc.perform(create(fullRequest("noplan-" + suffix, "PLATINUM")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
    }

    @Test
    void refusesAMissingMandatoryField() throws Exception {
        mvc.perform(create("""
                        {"subdomain": "noname-%s", "planCode": "PRO", "period": "MONTHLY", "startDate": "2026-12-01"}
                        """.formatted(suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:validation-failed"))
                .andExpect(jsonPath("$.title").value("Données invalides"));

        mvc.perform(create("""
                        {"name": "Sans date", "subdomain": "nodate-%s", "planCode": "PRO", "period": "MONTHLY"}
                        """.formatted(suffix)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnIceThatIsNotFifteenDigits() throws Exception {
        mvc.perform(create("""
                        {"name": "Atlas", "subdomain": "ice-%s", "ice": "12AB", "planCode": "PRO",
                         "period": "MONTHLY", "startDate": "2026-12-01"}
                        """.formatted(suffix)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForAnUnknownAgency() throws Exception {
        mvc.perform(get(AGENCIES + "/" + UUID.randomUUID()).with(platform()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
    }

    @Test
    void documentsTheNewEndpointsInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/backoffice/agencies']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/backoffice/agencies/{agencyId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/backoffice/agencies/{agencyId}/modules/{moduleCode}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me/modules']").exists());
    }

    /** Ni message de Jackson, ni nom de classe, ni trace dans la reponse. */
    private static void assertNoInternalDetails(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContainIgnoringCase("jackson")
                .doesNotContainIgnoringCase("parse")
                .doesNotContainIgnoringCase("deserialize")
                .doesNotContain("SPACE_TRAVEL")
                .doesNotContain("ma.agencysuite")
                .doesNotContain("Exception")
                .doesNotContain("line:");
    }

    private static MockHttpServletRequestBuilder create(String json) {
        return post(AGENCIES).with(platform()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String fullRequest(String subdomain, String planCode) {
        return """
                {
                  "name": "Atlas Voyages",
                  "subdomain": "%s",
                  "legalName": "Atlas Voyages SARL",
                  "ice": "001234567000089",
                  "ifNumber": "12345678",
                  "rc": "98765",
                  "patente": "123456",
                  "address": "12, avenue Mohammed V, Marrakech",
                  "planCode": "%s",
                  "period": "YEARLY",
                  "startDate": "2026-12-01",
                  "modules": ["TICKETING", "TRAVEL"]
                }
                """.formatted(subdomain, planCode);
    }
}
