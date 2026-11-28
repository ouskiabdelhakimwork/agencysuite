package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.supplier.SupplierApi.expectProblem;
import static ma.agencysuite.supplier.SupplierApi.product;
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

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.supplier.SupplierApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API des prestations : nom par langue (fr, ar, en) stocke en JSONB, recherche dans toutes les
 * langues, fournisseur archive, filtre supplierArchived, isolation entre agences.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductApiTest {

    private static final String DOUBLE_AR = "غرفة مزدوجة نصف إقامة";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    private final String suffix = PlatformTestData.randomSuffix();
    private SupplierApi api;
    private UUID agencyA;
    private UUID agencyB;
    private UUID riad;

    @BeforeEach
    void createAgencies() throws Exception {
        api = new SupplierApi(mvc);
        agencyA = data.createAgency("prod-a-" + suffix);
        agencyB = data.createAgency("prod-b-" + suffix);
        riad = api.createSupplierOk(agency(agencyA), supplier("RIAD", "Riad Atlas Test"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createsAProductNamedInFrenchOnly() throws Exception {
        var response = api.createProduct(agency(agencyA), riad, room("  Chambre double  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.name.fr").value("Chambre double"))
                .andExpect(jsonPath("$.name", not(hasKey("ar"))))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.supplier.id").value(riad.toString()))
                .andExpect(jsonPath("$.supplier.name").value("Riad Atlas Test"))
                .andExpect(jsonPath("$.supplier.archived").value(false))
                .andExpect(jsonPath("$.rates").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$", not(hasKey("searchKey"))))
                .andReturn().getResponse();
        String id = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(SupplierApi.PRODUCTS + "/" + id);
        api.getProduct(agency(agencyA), UUID.fromString(id)).andExpect(jsonPath("$.name.fr").value("Chambre double"));
    }

    @Test
    void createsAProductNamedInArabicOnly() throws Exception {
        UUID id = api.createProductOk(agency(agencyA), riad, product("ACCOMMODATION", Map.of("ar", DOUBLE_AR)));

        api.getProduct(agency(agencyA), id)
                .andExpect(jsonPath("$.name.ar").value(DOUBLE_AR))
                .andExpect(jsonPath("$.name", not(hasKey("fr"))));
    }

    @Test
    void storesTheThreeLanguagesAndTheDescriptionInJsonb() throws Exception {
        var body = product("EXCURSION", Map.of("fr", "Excursion Ourika", "ar", "رحلة أوريكا", "en", "Ourika trip"));
        body.put("description", Map.of("fr", "Journée complète", "en", " "));
        body.put("cancellationPolicy", "Gratuite jusqu'a J-2");

        UUID id = api.createProductOk(agency(agencyA), riad, body);

        api.getProduct(agency(agencyA), id)
                .andExpect(jsonPath("$.name.fr").value("Excursion Ourika"))
                .andExpect(jsonPath("$.name.ar").value("رحلة أوريكا"))
                .andExpect(jsonPath("$.name.en").value("Ourika trip"))
                .andExpect(jsonPath("$.description.fr").value("Journée complète"))
                .andExpect(jsonPath("$.description", not(hasKey("en"))))
                .andExpect(jsonPath("$.cancellationPolicy").value("Gratuite jusqu'a J-2"));
        // Ce qui est reellement en base : objet JSONB par langue, langue vide absente.
        Object[] row = (Object[]) TenantContext.callAsPlatform(() -> transaction.execute(status -> entityManager
                .createNativeQuery("SELECT jsonb_typeof(name), name ->> 'ar', description::text FROM product WHERE id = :id")
                .setParameter("id", id).getSingleResult()));
        assertThat(row[0]).isEqualTo("object");
        assertThat(row[1]).isEqualTo("رحلة أوريكا");
        assertThat((String) row[2]).contains("Journée complète").doesNotContain("\"en\"");
    }

    @Test
    void refusesANameWithoutLanguageOrWithAnUnknownOne() throws Exception {
        String code = "validation-failed";
        expectProblem(api.createProduct(agency(agencyA), riad, product("ACCOMMODATION", Map.of())), 400, code);
        expectProblem(api.createProduct(agency(agencyA), riad, product("ACCOMMODATION", Map.of("fr", "  "))), 400, code);
        Map<String, Object> noName = new LinkedHashMap<>();
        noName.put("type", "ACCOMMODATION");
        expectProblem(api.createProduct(agency(agencyA), riad, noName), 400, code);
        expectProblem(api.createProduct(agency(agencyA), riad,
                product("ACCOMMODATION", Map.of("fr", "Chambre", "es", "Habitacion"))), 400, code);
        expectProblem(api.createProduct(agency(agencyA), riad,
                product("ACCOMMODATION", Map.of("fr", "x".repeat(201)))), 400, code);
        expectProblem(api.createProduct(agency(agencyA), riad,
                with(room("Chambre"), "description", Map.of("fr", "x".repeat(2001)))), 400, code);
        expectProblem(api.createProduct(agency(agencyA), riad, product("SPA", Map.of("fr", "Hammam"))), 400,
                "malformed-request");
    }

    @Test
    void searchesTheNameInEveryLanguageWithoutAccentsOrHyphens() throws Exception {
        UUID halfBoard = api.createProductOk(agency(agencyA), riad,
                product("ACCOMMODATION", Map.of("fr", "Chambre double Demi-pension", "ar", DOUBLE_AR)));
        UUID transfer = api.createProductOk(agency(agencyA), riad,
                product("TRANSFER", Map.of("fr", "Transfert aéroport", "en", "Airport transfer")));

        for (String q : new String[] {"chambre", "demi pension", "DEMI-PENSION", "نصف إقامة"}) {
            api.listProducts(agency(agencyA), "q", q)
                    .andExpect(jsonPath("$.items[*].id").value(contains(halfBoard.toString())));
        }
        api.listProducts(agency(agencyA), "q", "aeroport")
                .andExpect(jsonPath("$.items[*].id").value(contains(transfer.toString())));
        api.listProducts(agency(agencyA), "q", "airport")
                .andExpect(jsonPath("$.items[*].id").value(contains(transfer.toString())));
        api.listProducts(agency(agencyA), "type", "TRANSFER")
                .andExpect(jsonPath("$.items[*].id").value(contains(transfer.toString())));
        api.listProducts(agency(agencyA), "supplierId", riad.toString()).andExpect(jsonPath("$.totalElements").value(2));
        api.listProducts(agency(agencyA))
                .andExpect(jsonPath("$.items[*].name.fr").value(contains("Chambre double Demi-pension",
                        "Transfert aéroport")))
                .andExpect(jsonPath("$.items[0].supplier.name").value("Riad Atlas Test"))
                .andExpect(jsonPath("$.items[0].archived").value(false));
        api.listProducts(agency(agencyA), "sort", "-updatedAt")
                .andExpect(jsonPath("$.items[0].id").value(transfer.toString()));
    }

    @Test
    void aProductCannotBeCreatedForAnArchivedSupplier() throws Exception {
        UUID existing = api.createProductOk(agency(agencyA), riad, room("Chambre double"));
        api.archiveSupplier(agency(agencyA), riad).andExpect(status().isOk());

        expectProblem(api.createProduct(agency(agencyA), riad, room("Suite")), 409, "supplier-archived");
        api.getProduct(agency(agencyA), existing)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.supplier.archived").value(true));
        expectProblem(api.createProduct(agency(agencyA), UUID.randomUUID(), room("Suite")), 404, "not-found");
    }

    @Test
    void productsOfArchivedSuppliersAreHiddenByDefault() throws Exception {
        UUID active = api.createProductOk(agency(agencyA), riad, room("Chambre active"));
        UUID hotel = api.createSupplierOk(agency(agencyA), supplier("HOTEL", "Hotel Ferme Test"));
        UUID hidden = api.createProductOk(agency(agencyA), hotel, room("Chambre masquee"));
        api.archiveSupplier(agency(agencyA), hotel).andExpect(status().isOk());

        api.listProducts(agency(agencyA)).andExpect(jsonPath("$.items[*].id").value(contains(active.toString())));
        api.listProducts(agency(agencyA), "supplierArchived", "true")
                .andExpect(jsonPath("$.items[*].id").value(contains(hidden.toString())))
                .andExpect(jsonPath("$.items[0].supplier.archived").value(true));
        // Archiver le fournisseur n'archive pas ses prestations.
        api.getProduct(agency(agencyA), hidden).andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void updatesAProductWithoutChangingItsSupplier() throws Exception {
        UUID id = api.createProductOk(agency(agencyA), riad, room("Chambre double"));

        api.updateProduct(agency(agencyA), id, 0,
                        with(product("ACCOMMODATION", Map.of("fr", "Chambre double vue", "en", "Double room")),
                                "supplierId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.fr").value("Chambre double vue"))
                .andExpect(jsonPath("$.name.en").value("Double room"))
                .andExpect(jsonPath("$.supplier.id").value(riad.toString()))
                .andExpect(jsonPath("$.version").value(1));
        api.listProducts(agency(agencyA), "q", "double room").andExpect(jsonPath("$.totalElements").value(1));
        expectProblem(api.updateProduct(agency(agencyA), id, 0, room("Perimee")), 409, "stale-version");
    }

    @Test
    void aProductOfAnotherAgencyIsNotFound() throws Exception {
        UUID id = api.createProductOk(agency(agencyA), riad, room("Chambre secrete"));

        expectProblem(api.getProduct(agency(agencyB), id), 404, "not-found");
        expectProblem(api.updateProduct(agency(agencyB), id, 0, room("Pirate")), 404, "not-found");
        expectProblem(api.archiveProduct(agency(agencyB), id), 404, "not-found");
        expectProblem(api.listRates(agency(agencyB), id), 404, "not-found");
        api.listProducts(agency(agencyB)).andExpect(jsonPath("$.totalElements").value(0));
        api.listProducts(agency(agencyB), "q", "secrete").andExpect(jsonPath("$.totalElements").value(0));
        api.listProducts(agency(agencyB), "supplierId", riad.toString()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
