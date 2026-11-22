package ma.agencysuite.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Test de bout en bout du socle : contexte Spring complet, i18n FR/AR/EN avec
 * sens d'ecriture, format d'erreur RFC 9457 et documentation OpenAPI.
 */
@SpringBootTest
class PlatformInfoApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void returnsFrenchByDefault() throws Exception {
        mvc.perform(get("/api/v1/platform/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("fr"))
                .andExpect(jsonPath("$.direction").value("ltr"))
                .andExpect(jsonPath("$.welcome").value("Bienvenue sur AgencySuite"))
                .andExpect(jsonPath("$.supportedLocales.length()").value(3));
    }

    @Test
    void returnsArabicRightToLeftForMoroccanArabic() throws Exception {
        mvc.perform(get("/api/v1/platform/info").header(HttpHeaders.ACCEPT_LANGUAGE, "ar-MA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("ar"))
                .andExpect(jsonPath("$.direction").value("rtl"))
                .andExpect(jsonPath("$.welcome").value("مرحبًا بكم في AgencySuite"));
    }

    @Test
    void returnsEnglish() throws Exception {
        mvc.perform(get("/api/v1/platform/info").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.welcome").value("Welcome to AgencySuite"));
    }

    @Test
    void fallsBackToFrenchForUnsupportedLanguage() throws Exception {
        mvc.perform(get("/api/v1/platform/info").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("fr"));
    }

    @Test
    void unknownRouteReturnsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/platform/info']").exists());
    }
}
