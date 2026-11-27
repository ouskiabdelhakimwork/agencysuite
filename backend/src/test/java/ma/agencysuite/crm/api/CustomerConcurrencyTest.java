package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.CrmApi.company;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Verrouillage optimiste : deux modifications de la meme version, la seconde recoit 409 stale-version. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerConcurrencyTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new CrmApi(mvc);
        agencyId = data.createAgency("lock-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void theSecondUpdateOfTheSameVersionIsRejected() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));
        long read = api.version(agency(agencyId), id);

        api.update(agency(agencyId), id, read, company("Premiere Modification Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(read + 1));
        api.expectProblem(api.update(agency(agencyId), id, read, company("Seconde Modification Test")), 409,
                "stale-version");

        api.get(agency(agencyId), id)
                .andExpect(jsonPath("$.name").value("Premiere Modification Test"))
                .andExpect(jsonPath("$.version").value(read + 1));
    }

    @Test
    void theStaleVersionProblemIsTranslated() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));
        api.update(agency(agencyId), id, 0, company("Modifiee Test")).andExpect(status().isOk());

        staleUpdate(id, "fr")
                .andExpect(jsonPath("$.detail").value("La fiche a été modifiée par quelqu’un d’autre ; rechargez-la."));
        staleUpdate(id, "en").andExpect(jsonPath("$.title").value("Record changed in the meantime"));
        staleUpdate(id, "ar").andExpect(jsonPath("$.title").value("تم تعديل البطاقة في الأثناء"));
    }

    @Test
    void aFutureVersionIsAlsoStale() throws Exception {
        UUID id = api.createOk(agency(agencyId), company("Societe Test"));

        api.expectProblem(api.update(agency(agencyId), id, 7, company("Modifiee Test")), 409, "stale-version");
    }

    private org.springframework.test.web.servlet.ResultActions staleUpdate(UUID id, String language)
            throws Exception {
        return api.perform(MockMvcRequestBuilders.put(CrmApi.CUSTOMERS + "/" + id).with(agency(agencyId))
                        .header("Accept-Language", language).contentType(MediaType.APPLICATION_JSON)
                        .content(CrmApi.json(CrmApi.with(company("Perimee Test"), "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:stale-version"));
    }
}
