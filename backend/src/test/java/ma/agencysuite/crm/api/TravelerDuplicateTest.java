package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static ma.agencysuite.crm.TravelerApi.with;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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

/**
 * Alerte de doublon probable sur les voyageurs : meme nom et meme date de naissance, meme e-mail ou
 * meme telephone (le nom seul n'est pas un critere), dans l'agence seulement, hors archives.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerDuplicateTest {

    private static final String BIRTH_DATE = "1990-05-14";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new TravelerApi(mvc);
        agencyA = data.createAgency("tdup-a-" + suffix);
        agencyB = data.createAgency("tdup-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void sameNameAndSameBirthDateIs409() throws Exception {
        UUID existing = api.createOk(agency(agencyA), with(traveler("Él Fassi", "Zineb"), "birthDate", BIRTH_DATE));

        api.expectProblem(api.create(agency(agencyA), with(traveler("el  fassi", "ZINEB"), "birthDate", BIRTH_DATE)),
                409, "possible-duplicate");
        api.create(agency(agencyA), with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE))
                .andExpect(jsonPath("$.candidates[*].id").value(contains(existing.toString())))
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("NAME_AND_BIRTH_DATE")));
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sameNameAloneIsNotADuplicate() throws Exception {
        api.createOk(agency(agencyA), with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE));

        api.create(agency(agencyA), traveler("El Fassi", "Zineb")).andExpect(status().isCreated());
        api.create(agency(agencyA), with(traveler("El Fassi", "Zineb"), "birthDate", "1991-01-01"))
                .andExpect(status().isCreated());
    }

    @Test
    void sameEmailOrPhoneIs409() throws Exception {
        var body = traveler("Bennani", "Salma");
        body.put("email", "salma@example.test");
        body.put("phone", "0612345678");
        UUID existing = api.createOk(agency(agencyA), body);

        api.create(agency(agencyA), with(traveler("Autre", "Test"), "email", "SALMA@example.test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:possible-duplicate"))
                .andExpect(jsonPath("$.candidates[0].id").value(existing.toString()))
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("EMAIL")));
        api.create(agency(agencyA), with(traveler("Autre", "Test"), "phone", "+212 6 12 34 56 78"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("PHONE")));
        var both = with(traveler("Autre", "Test"), "email", "salma@example.test");
        both.put("phone", "0612345678");
        api.create(agency(agencyA), both)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].criteria").value(containsInAnyOrder("EMAIL", "PHONE")));
    }

    @Test
    void confirmDuplicateCreatesAnyway() throws Exception {
        api.createOk(agency(agencyA), with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE));

        var again = with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE);
        again.put("confirmDuplicate", true);
        api.create(agency(agencyA), again).andExpect(status().isCreated());
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void anArchivedTravelerIsNotACandidate() throws Exception {
        UUID archived = api.createOk(agency(agencyA), with(traveler("Archive", "Test"), "email", "x@example.test"));
        api.archive(agency(agencyA), archived).andExpect(status().isOk());

        api.create(agency(agencyA), with(traveler("Nouveau", "Test"), "email", "x@example.test"))
                .andExpect(status().isCreated());
    }

    @Test
    void aTravelerOfAnotherAgencyIsNeverProposed() throws Exception {
        var body = with(traveler("El Fassi", "Zineb"), "birthDate", BIRTH_DATE);
        body.put("email", "zineb@example.test");
        body.put("phone", "0612345678");
        api.createOk(agency(agencyB), body);

        api.create(agency(agencyA), body).andExpect(status().isCreated());
    }

    @Test
    void updateChecksOnlyWhenACriterionChanges() throws Exception {
        api.createOk(agency(agencyA), with(traveler("Premier", "Test"), "email", "premier@example.test"));
        var second = with(traveler("Second", "Test"), "email", "second@example.test");
        second.put("birthDate", BIRTH_DATE);
        UUID id = api.createOk(agency(agencyA), second);
        // Doublon cree volontairement : meme e-mail que le premier.
        api.update(agency(agencyA), id, 0, with(with(second, "email", "premier@example.test"), "confirmDuplicate",
                true)).andExpect(status().isOk());

        // Aucun critere ne change (seules les notes) : pas de controle, malgre le doublon existant.
        api.update(agency(agencyA), id, 1, with(with(second, "email", "premier@example.test"), "notes", "Note"))
                .andExpect(status().isOk());
        // L'e-mail redevient distinct, puis le telephone change vers un numero libre : pas de conflit.
        api.update(agency(agencyA), id, 2, with(second, "phone", "0612345678")).andExpect(status().isOk());
        // Le nom change vers celui d'un voyageur de meme date : 409.
        api.createOk(agency(agencyA), with(traveler("Troisieme", "Test"), "birthDate", BIRTH_DATE));
        api.expectProblem(api.update(agency(agencyA), id, 3, with(with(second, "lastName", "Troisieme"), "phone",
                "0612345678")), 409, "possible-duplicate");
        // Sa propre fiche n'est jamais candidate.
        api.update(agency(agencyA), id, 3, with(with(second, "phone", "0612345678"), "firstName", "Test Bis"))
                .andExpect(status().isOk());
    }
}
