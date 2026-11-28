package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.supplier.SupplierApi.expectProblem;
import static ma.agencysuite.supplier.SupplierApi.room;
import static ma.agencysuite.supplier.SupplierApi.supplier;
import static ma.agencysuite.supplier.SupplierApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.supplier.SupplierApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API des fournisseurs (/api/v1/suppliers) : creation, normalisation, validation, recherche,
 * filtres, tri, pagination, nombre de prestations, permissions et isolation (US-601).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupplierApiTest {

    private static final String ICE = "001234567000089";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private SupplierApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new SupplierApi(mvc);
        agencyA = data.createAgency("sup-a-" + suffix);
        agencyB = data.createAgency("sup-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createsASupplierWithDefaultsAndNormalizedContactDetails() throws Exception {
        var body = supplier("RIAD", "  Riad Atlas Test  ");
        body.put("city", "Marrakech");
        body.put("phone", "0524000000");
        body.put("email", "  Reservation@Riad-Atlas.EXAMPLE.test ");

        var response = api.createSupplier(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RIAD"))
                .andExpect(jsonPath("$.name").value("Riad Atlas Test"))
                .andExpect(jsonPath("$.phone").value("+212524000000"))
                .andExpect(jsonPath("$.email").value("reservation@riad-atlas.example.test"))
                .andExpect(jsonPath("$.country").value("MA"))
                .andExpect(jsonPath("$.defaultCurrency").value("MAD"))
                .andExpect(jsonPath("$.ice").value(nullValue()))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$", not(hasKey("nameKey"))))
                .andReturn().getResponse();
        String id = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(SupplierApi.SUPPLIERS + "/" + id);
        api.getSupplier(agency(agencyA), UUID.fromString(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Marrakech"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void storesAllFieldsIncludingAnIceForAnyType() throws Exception {
        var body = supplier("TRANSPORT", "Transports Sud Test");
        body.put("ice", ICE);
        body.put("contactName", "Responsable Test");
        body.put("addressLine", "1 rue de Test");
        body.put("city", "Agadir");
        body.put("country", "FR");
        body.put("defaultCurrency", "EUR");
        body.put("paymentTerms", "30 % a la reservation, solde J-15");
        body.put("notes", "Fournisseur de test");

        api.createSupplier(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ice").value(ICE))
                .andExpect(jsonPath("$.contactName").value("Responsable Test"))
                .andExpect(jsonPath("$.country").value("FR"))
                .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
                .andExpect(jsonPath("$.paymentTerms").value("30 % a la reservation, solde J-15"));
    }

    @Test
    void refusesInvalidInput() throws Exception {
        String code = "validation-failed";
        expectProblem(api.createSupplier(agency(agencyA), supplier("CASTLE", "Inconnu Test")), 400,
                "malformed-request");
        expectProblem(api.createSupplier(agency(agencyA), supplier("HOTEL", "  ")), 400, code);
        expectProblem(api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Hotel Test"), "ice", "1234")), 400,
                code);
        expectProblem(api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Hotel Test"), "email", "pas-un-mail")),
                400, code);
        expectProblem(api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Hotel Test"), "phone", "12")), 400,
                code);
        expectProblem(api.createSupplier(agency(agencyA), with(supplier("HOTEL", "Hotel Test"), "country", "ma")), 400,
                code);
        expectProblem(api.createSupplier(agency(agencyA),
                with(supplier("HOTEL", "Hotel Test"), "defaultCurrency", "eur")), 400, code);
        expectProblem(api.createSupplier(agency(agencyA),
                with(supplier("HOTEL", "Hotel Test"), "defaultCurrency", "ZZZ")), 400, code);
        expectProblem(api.createSupplier(agency(agencyA),
                with(supplier("HOTEL", "Hotel Test"), "paymentTerms", "x".repeat(501))), 400, code);
    }

    @Test
    void searchesByNameWithoutAccentsPhoneIceAndCity() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyA),
                with(with(supplier("RIAD", "Riad Él Médina Test"), "city", "Fès"), "phone", "0535000000"));
        UUID hotel = api.createSupplierOk(agency(agencyA),
                with(with(supplier("HOTEL", "Hotel Ocean Test"), "city", "Agadir"), "ice", ICE));

        api.listSuppliers(agency(agencyA), "q", "el medina")
                .andExpect(jsonPath("$.items[*].id").value(contains(riad.toString())));
        api.listSuppliers(agency(agencyA), "q", "0535000000")
                .andExpect(jsonPath("$.items[*].id").value(contains(riad.toString())));
        api.listSuppliers(agency(agencyA), "q", "+212535000000")
                .andExpect(jsonPath("$.items[*].id").value(contains(riad.toString())));
        api.listSuppliers(agency(agencyA), "q", "4567000")
                .andExpect(jsonPath("$.items[*].id").value(contains(hotel.toString())));
        api.listSuppliers(agency(agencyA), "q", "agadir")
                .andExpect(jsonPath("$.items[*].id").value(contains(hotel.toString())));
        api.listSuppliers(agency(agencyA), "q", "100%").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByTypeAndCity() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyA), with(supplier("RIAD", "Riad Test"), "city", "Marrakech"));
        api.createSupplierOk(agency(agencyA), with(supplier("HOTEL", "Hotel Test"), "city", "Marrakech"));
        api.createSupplierOk(agency(agencyA), with(supplier("RIAD", "Riad Autre Test"), "city", "Fes"));

        api.listSuppliers(agency(agencyA), "type", "RIAD", "city", "marrakech")
                .andExpect(jsonPath("$.items[*].id").value(contains(riad.toString())));
        api.listSuppliers(agency(agencyA), "type", "RIAD").andExpect(jsonPath("$.totalElements").value(2));
        api.listSuppliers(agency(agencyA), "city", "Marrakech").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void sortsByNameWithoutAccentsOrByDateAndPaginates() throws Exception {
        api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Charlie Test"));
        api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Élan Test"));
        api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Alpha Test"));

        api.listSuppliers(agency(agencyA))
                .andExpect(jsonPath("$.items[*].name").value(contains("Alpha Test", "Charlie Test", "Élan Test")));
        api.listSuppliers(agency(agencyA), "sort", "-createdAt")
                .andExpect(jsonPath("$.items[*].name").value(contains("Alpha Test", "Élan Test", "Charlie Test")));
        api.listSuppliers(agency(agencyA), "sort", "-updatedAt").andExpect(jsonPath("$.items[0].name").value("Alpha Test"));
        api.listSuppliers(agency(agencyA), "size", "2", "page", "1")
                .andExpect(jsonPath("$.items[*].name").value(contains("Élan Test")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2));
        expectProblem(api.listSuppliers(agency(agencyA), "size", "101"), 400, "validation-failed");
        expectProblem(api.listSuppliers(agency(agencyA), "sort", "city"), 400, "validation-failed");
    }

    @Test
    void theListShowsTheNumberOfActiveProducts() throws Exception {
        UUID riad = api.createSupplierOk(agency(agencyA), with(supplier("RIAD", "Riad Test"), "defaultCurrency", "EUR"));
        api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Sans Prestation Test"));
        api.createProductOk(agency(agencyA), riad, room("Chambre double"));
        UUID archived = api.createProductOk(agency(agencyA), riad, room("Suite"));
        api.archiveProduct(agency(agencyA), archived).andExpect(status().isOk());

        api.listSuppliers(agency(agencyA))
                .andExpect(jsonPath("$.items[0].name").value("Riad Test"))
                .andExpect(jsonPath("$.items[0].productCount").value(1))
                .andExpect(jsonPath("$.items[0].defaultCurrency").value("EUR"))
                .andExpect(jsonPath("$.items[0].country").value("MA"))
                .andExpect(jsonPath("$.items[1].productCount").value(0));
    }

    @Test
    void updatesASupplierWithItsVersion() throws Exception {
        UUID id = api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Hotel Test"));

        api.updateSupplier(agency(agencyA), id, 0, with(supplier("DMC", "Agence Receptive Test"), "city", "Rabat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DMC"))
                .andExpect(jsonPath("$.name").value("Agence Receptive Test"))
                .andExpect(jsonPath("$.version").value(1));
        expectProblem(api.updateSupplier(agency(agencyA), id, 0, supplier("DMC", "Perimee Test")), 409,
                "stale-version");
        expectProblem(api.mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(SupplierApi.SUPPLIERS + "/" + id).with(agency(agencyA))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(SupplierApi.json(supplier("DMC", "Sans Version Test")))), 400, "validation-failed");
    }

    @Test
    void accountantAndSalesReadButCannotWrite() throws Exception {
        UUID id = api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Hotel Test"));
        UUID product = api.createProductOk(agency(agencyA), id, room("Chambre"));
        for (String role : new String[] {"ACCOUNTANT", "SALES"}) {
            var user = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, role));

            api.listSuppliers(user).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            api.getSupplier(user, id).andExpect(status().isOk());
            api.getProduct(user, product).andExpect(status().isOk());
            api.listRates(user, product).andExpect(status().isOk());
            expectProblem(api.createSupplier(user, supplier("HOTEL", "Interdit Test")), 403, "permission-denied");
            expectProblem(api.updateSupplier(user, id, 0, supplier("HOTEL", "Interdit Test")), 403,
                    "permission-denied");
            expectProblem(api.archiveSupplier(user, id), 403, "permission-denied");
            expectProblem(api.createProduct(user, id, room("Interdite")), 403, "permission-denied");
            expectProblem(api.createRate(user, product, SupplierApi.rate("DOUBLE", "2027-01-01", "2027-06-30",
                    "95.00", null)), 403, "permission-denied");
        }
    }

    @Test
    void operationsCanWrite() throws Exception {
        var operations = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, "OPERATIONS"));

        api.createSupplier(operations, supplier("GUIDE", "Guide Test")).andExpect(status().isCreated());
    }

    @Test
    void anotherAgencyNeitherSeesNorChangesTheSupplier() throws Exception {
        UUID id = api.createSupplierOk(agency(agencyA), with(supplier("HOTEL", "Hotel Secret Test"), "ice", ICE));

        expectProblem(api.getSupplier(agency(agencyB), id), 404, "not-found");
        expectProblem(api.updateSupplier(agency(agencyB), id, 0, supplier("HOTEL", "Pirate Test")), 404, "not-found");
        expectProblem(api.archiveSupplier(agency(agencyB), id), 404, "not-found");
        expectProblem(api.createProduct(agency(agencyB), id, room("Pirate")), 404, "not-found");
        api.listSuppliers(agency(agencyB)).andExpect(jsonPath("$.totalElements").value(0));
        api.listSuppliers(agency(agencyB), "q", ICE).andExpect(jsonPath("$.totalElements").value(0));
        api.getSupplier(agency(agencyA), id).andExpect(jsonPath("$.name").value("Hotel Secret Test"));
    }

    @Test
    void theNotFoundProblemIsTranslated() throws Exception {
        api.mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(SupplierApi.SUPPLIERS + "/" + UUID.randomUUID()).with(agency(agencyA))
                        .header("Accept-Language", "ar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("المورد غير موجود"));
    }
}
