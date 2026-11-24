package ma.agencysuite.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Valeur par defaut (unsecured-access=false) : le back-office est ferme. */
@SpringBootTest(properties = "agencysuite.backoffice.unsecured-access=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BackofficeDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void everyBackofficeCallIsForbidden() throws Exception {
        String agency = "/api/v1/backoffice/agencies/" + UUID.randomUUID();
        expectDisabled(post("/api/v1/backoffice/agencies").contentType(MediaType.APPLICATION_JSON).content("{}"));
        expectDisabled(get(agency));
        expectDisabled(put(agency + "/modules/TRAVEL"));
        expectDisabled(delete(agency + "/modules/TRAVEL"));
        expectDisabled(get("/api/v1/backoffice"));
    }

    @Test
    void errorIsTranslated() throws Exception {
        mvc.perform(get("/api/v1/backoffice/agencies/" + UUID.randomUUID()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Back-office disabled"));
    }

    @Test
    void otherEndpointsAreNotAffected() throws Exception {
        mvc.perform(get("/api/v1/platform/info")).andExpect(status().isOk());
    }

    private void expectDisabled(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:backoffice-disabled"));
    }
}
