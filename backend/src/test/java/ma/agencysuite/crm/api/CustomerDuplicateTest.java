package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.CrmApi.company;
import static ma.agencysuite.crm.CrmApi.individual;
import static ma.agencysuite.crm.CrmApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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

/**
 * Alerte de doublon probable (meme e-mail, telephone ou ICE qu'un client non archive de la meme
 * agence) a la creation et a la modification, et confirmation explicite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerDuplicateTest {

    private static final String ICE = "001234567000089";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new CrmApi(mvc);
        agencyA = data.createAgency("dup-a-" + suffix);
        agencyB = data.createAgency("dup-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void sameEmailIsAPossibleDuplicateWhateverTheCase() throws Exception {
        UUID existing = api.createOk(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"));

        String body = api.expectProblem(api.create(agency(agencyA),
                with(individual("K. Test"), "email", "KARIM@Example.test")), 409, "possible-duplicate");

        assertThat(candidates(body)).containsExactly(Map.of("id", existing.toString(), "name", "Karim Test",
                "criteria", List.of("EMAIL")));
        assertThat(body).doesNotContain("karim@example.test");
    }

    @Test
    void samePhoneInAnotherFormatIsAPossibleDuplicate() throws Exception {
        UUID existing = api.createOk(agency(agencyA), with(company("Atlas Pharma Test"), "phone", "0522000000"));

        String body = api.expectProblem(api.create(agency(agencyA),
                with(company("Atlas Bis Test"), "phone", "+212 5 22 00 00 00")), 409, "possible-duplicate");

        assertThat(candidates(body)).containsExactly(Map.of("id", existing.toString(), "name", "Atlas Pharma Test",
                "criteria", List.of("PHONE")));
    }

    @Test
    void sameIceIsAPossibleDuplicate() throws Exception {
        UUID existing = api.createOk(agency(agencyA), with(company("Atlas Pharma Test"), "ice", ICE));

        String body = api.expectProblem(api.create(agency(agencyA), with(company("Atlas Bis Test"), "ice", ICE)),
                409, "possible-duplicate");

        assertThat(candidates(body)).extracting(c -> c.get("id"), c -> c.get("criteria"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(existing.toString(), List.of("ICE")));
    }

    @Test
    void allCommonCriteriaAndAllCandidatesAreListed() throws Exception {
        var full = company("Atlas Pharma Test");
        full.put("email", "contact@atlas.example.test");
        full.put("phone", "0522000000");
        UUID both = api.createOk(agency(agencyA), full);
        UUID byIce = api.createOk(agency(agencyA), with(company("Autre Societe Test"), "ice", ICE));

        var candidate = with(full, "ice", ICE);
        String body = api.expectProblem(api.create(agency(agencyA), with(candidate, "name", "Nouvelle Test")), 409,
                "possible-duplicate");

        assertThat(candidates(body)).containsExactlyInAnyOrder(
                Map.of("id", both.toString(), "name", "Atlas Pharma Test", "criteria", List.of("EMAIL", "PHONE")),
                Map.of("id", byIce.toString(), "name", "Autre Societe Test", "criteria", List.of("ICE")));
    }

    @Test
    void confirmDuplicateCreatesAnyway() throws Exception {
        api.createOk(agency(agencyA), with(individual("Karim Test"), "phone", "0612345678"));
        var again = with(individual("Karim Test"), "phone", "0612345678");

        api.expectProblem(api.create(agency(agencyA), again), 409, "possible-duplicate");
        api.create(agency(agencyA), with(again, "confirmDuplicate", true)).andExpect(status().isCreated());
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void anArchivedCustomerIsNotACandidate() throws Exception {
        UUID archived = api.createOk(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"));
        api.archive(agency(agencyA), archived).andExpect(status().isOk());

        api.create(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"))
                .andExpect(status().isCreated());
    }

    @Test
    void aCustomerOfAnotherAgencyIsNeverProposed() throws Exception {
        var body = company("Atlas Pharma Test");
        body.put("email", "contact@atlas.example.test");
        body.put("phone", "0522000000");
        body.put("ice", ICE);
        api.createOk(agency(agencyB), body);

        api.create(agency(agencyA), body).andExpect(status().isCreated());
    }

    @Test
    void updateChecksDuplicatesOnlyWhenAnIdentifierChanges() throws Exception {
        UUID karim = api.createOk(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"));
        UUID other = api.createOk(agency(agencyA), with(individual("Autre Test"), "email", "autre@example.test"));

        String body = api.expectProblem(api.update(agency(agencyA), other, 0,
                with(individual("Autre Test"), "email", "Karim@example.test")), 409, "possible-duplicate");
        assertThat(candidates(body)).extracting(c -> c.get("id")).containsExactly(karim.toString());

        api.update(agency(agencyA), other, 0, with(with(individual("Autre Test"), "email", "karim@example.test"),
                "confirmDuplicate", true)).andExpect(status().isOk());
        // Les deux fiches partagent l'e-mail : modifier autre chose ne redeclenche pas l'alerte,
        // et le client n'est jamais son propre doublon.
        api.update(agency(agencyA), other, 1, with(individual("Autre Renomme Test"), "email", "karim@example.test"))
                .andExpect(status().isOk());
        api.update(agency(agencyA), karim, 0, with(individual("Karim Test"), "email", "karim@example.test"))
                .andExpect(status().isOk());
    }

    @Test
    void theProblemIsTranslatedInArabic() throws Exception {
        api.createOk(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"));

        api.perform(MockMvcRequestBuilders.post(CrmApi.CUSTOMERS).with(agency(agencyA))
                        .header("Accept-Language", "ar").contentType(MediaType.APPLICATION_JSON)
                        .content(CrmApi.json(with(individual("Karim Test"), "email", "karim@example.test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:possible-duplicate"))
                .andExpect(jsonPath("$.title").value("تكرار محتمل"))
                .andExpect(jsonPath("$.candidates[0].name").value("Karim Test"));
    }

    private static List<Map<String, Object>> candidates(String body) {
        return JsonPath.read(body, "$.candidates");
    }
}
