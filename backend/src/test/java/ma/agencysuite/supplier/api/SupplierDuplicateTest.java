package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.supplier.SupplierApi.expectProblem;
import static ma.agencysuite.supplier.SupplierApi.supplier;
import static ma.agencysuite.supplier.SupplierApi.with;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.supplier.SupplierApi;
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
 * Doublon probable des fournisseurs : meme ICE, e-mail ou telephone qu'un fournisseur non archive
 * de l'agence ; le nom seul n'est pas un critere ; confirmation explicite ; jamais un fournisseur
 * d'une autre agence. Meme forme de reponse que pour les clients.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupplierDuplicateTest {

    private static final String ICE = "001234567000089";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private SupplierApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new SupplierApi(mvc);
        agencyA = data.createAgency("dup-sa-" + suffix);
        agencyB = data.createAgency("dup-sb-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void sameIceEmailOrPhoneIsAPossibleDuplicate() throws Exception {
        UUID existing = api.createSupplierOk(agency(agencyA), withIdentifiers(supplier("HOTEL", "Hotel Atlas Test")));

        String body = expectProblem(api.createSupplier(agency(agencyA),
                with(supplier("HOTEL", "Autre Nom Test"), "ice", ICE)), 409, "possible-duplicate");
        org.assertj.core.api.Assertions.assertThat(body).contains("\"candidates\"");
        api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Autre Nom Test"), "ice", ICE))
                .andExpect(jsonPath("$.title").value("Doublon probable"))
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.candidates[0].id").value(existing.toString()))
                .andExpect(jsonPath("$.candidates[0].name").value("Hotel Atlas Test"))
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("ICE")));
        api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Autre Test"), "email", " CONTACT@atlas.example.test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("EMAIL")));
        api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Autre Test"), "phone", "+212 522 00 00 00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("PHONE")));
        api.createSupplier(agency(agencyA), withIdentifiers(supplier("RIAD", "Encore Test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.candidates[0].criteria").value(contains("ICE", "EMAIL", "PHONE")));
    }

    @Test
    void theNameAloneIsNotACriterion() throws Exception {
        api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Hotel Atlas Test"));

        api.createSupplier(agency(agencyA), supplier("HOTEL", "Hotel Atlas Test")).andExpect(status().isCreated());
    }

    @Test
    void confirmDuplicateCreatesAnyway() throws Exception {
        api.createSupplierOk(agency(agencyA), withIdentifiers(supplier("HOTEL", "Hotel Atlas Test")));

        api.createSupplier(agency(agencyA),
                        with(withIdentifiers(supplier("HOTEL", "Hotel Atlas Bis Test")), "confirmDuplicate", true))
                .andExpect(status().isCreated());
        api.listSuppliers(agency(agencyA)).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void anArchivedSupplierIsNotACandidate() throws Exception {
        UUID archived = api.createSupplierOk(agency(agencyA), withIdentifiers(supplier("HOTEL", "Hotel Ancien Test")));
        api.archiveSupplier(agency(agencyA), archived).andExpect(status().isOk());

        api.createSupplier(agency(agencyA), withIdentifiers(supplier("HOTEL", "Hotel Nouveau Test")))
                .andExpect(status().isCreated());
    }

    @Test
    void aSupplierOfAnotherAgencyIsNeverProposed() throws Exception {
        api.createSupplierOk(agency(agencyB), withIdentifiers(supplier("HOTEL", "Hotel Autre Agence Test")));

        api.createSupplier(agency(agencyA), withIdentifiers(supplier("HOTEL", "Hotel Test")))
                .andExpect(status().isCreated());
    }

    @Test
    void anUpdateIsCheckedOnlyWhenAnIdentifierChanges() throws Exception {
        api.createSupplierOk(agency(agencyA), with(supplier("HOTEL", "Hotel Atlas Test"), "ice", ICE));
        UUID other = api.createSupplierOk(agency(agencyA),
                with(with(supplier("HOTEL", "Hotel Autre Test"), "ice", ICE), "confirmDuplicate", true));

        // Meme ICE qu'avant : pas de nouveau controle, la modification passe.
        api.updateSupplier(agency(agencyA), other, 0, with(supplier("HOTEL", "Hotel Autre Renomme Test"), "ice", ICE))
                .andExpect(status().isOk());
        // Nouvel e-mail identique a celui d'un autre fournisseur : 409, puis confirmation.
        api.createSupplierOk(agency(agencyA), with(supplier("HOTEL", "Hotel Mail Test"), "email", "mail@example.test"));
        api.updateSupplier(agency(agencyA), other, 1,
                        with(with(supplier("HOTEL", "Hotel Autre Renomme Test"), "ice", ICE), "email", "mail@example.test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:possible-duplicate"))
                .andExpect(jsonPath("$.candidates[*].name").value(contains("Hotel Atlas Test", "Hotel Mail Test")));
        api.updateSupplier(agency(agencyA), other, 1, with(with(with(supplier("HOTEL", "Hotel Autre Renomme Test"),
                        "ice", ICE), "email", "mail@example.test"), "confirmDuplicate", true))
                .andExpect(status().isOk());
    }

    @Test
    void theProblemHasTheSameShapeAsForCustomers() throws Exception {
        api.createSupplierOk(agency(agencyA), with(supplier("HOTEL", "Hotel Atlas Test"), "ice", ICE));

        mvc.perform(MockMvcRequestBuilders.post(SupplierApi.SUPPLIERS).with(agency(agencyA))
                        .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                        .content(SupplierApi.json(with(supplier("HOTEL", "Autre Test"), "ice", ICE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:possible-duplicate"))
                .andExpect(jsonPath("$.title").value("Possible duplicate"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.candidates[0].id").isNotEmpty())
                .andExpect(jsonPath("$.candidates[0].name").value("Hotel Atlas Test"))
                .andExpect(jsonPath("$.candidates[0].criteria[0]").value("ICE"));
    }

    private static Map<String, Object> withIdentifiers(Map<String, Object> body) {
        var copy = with(body, "ice", ICE);
        copy.put("email", "contact@atlas.example.test");
        copy.put("phone", "0522000000");
        return copy;
    }
}
