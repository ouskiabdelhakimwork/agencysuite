package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.TravelerApi;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Verrouillage optimiste des voyageurs : une version perimee donne 409 stale-version. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerConcurrencyTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new TravelerApi(mvc);
        agencyId = data.createAgency("tlock-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void theSecondUpdateOfTheSameVersionIsRejected() throws Exception {
        UUID id = api.createOk(agency(agencyId), traveler("Bennani", "Salma"));
        long read = api.version(agency(agencyId), id);

        api.update(agency(agencyId), id, read, traveler("Bennani", "Premiere"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(read + 1));
        api.expectProblem(api.update(agency(agencyId), id, read, traveler("Bennani", "Seconde")), 409, "stale-version");
        api.expectProblem(api.update(agency(agencyId), id, read + 5, traveler("Bennani", "Future")), 409,
                "stale-version");

        api.get(agency(agencyId), id)
                .andExpect(jsonPath("$.firstName").value("Premiere"))
                .andExpect(jsonPath("$.version").value(read + 1));
    }
}
