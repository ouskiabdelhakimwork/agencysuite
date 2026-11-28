package ma.agencysuite.supplier.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.supplier.SupplierApi.expectProblem;
import static ma.agencysuite.supplier.SupplierApi.rate;
import static ma.agencysuite.supplier.SupplierApi.room;
import static ma.agencysuite.supplier.SupplierApi.supplier;
import static ma.agencysuite.supplier.SupplierApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * API des tarifs : devise du fournisseur par defaut ou devise explicite, montant en chaine exact
 * (jamais de nombre JSON, jamais d'arrondi), periode, filtres, independance vis-a-vis de la devise
 * par defaut du fournisseur, isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateApiTest {

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
    private UUID room;

    @BeforeEach
    void createCatalog() throws Exception {
        api = new SupplierApi(mvc);
        agencyA = data.createAgency("rate-a-" + suffix);
        agencyB = data.createAgency("rate-b-" + suffix);
        riad = api.createSupplierOk(agency(agencyA), with(supplier("RIAD", "Riad Atlas Test"), "defaultCurrency", "EUR"));
        room = api.createProductOk(agency(agencyA), riad, room("Chambre double demi-pension"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aRateIsInTheSupplierCurrencyByDefault() throws Exception {
        var body = with(rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null), "label", " Basse saison ");

        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(api.createRate(agency(agencyA), room, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(room.toString()))
                .andExpect(jsonPath("$.basis").value("DOUBLE"))
                .andExpect(jsonPath("$.validFrom").value("2027-01-01"))
                .andExpect(jsonPath("$.validTo").value("2027-06-30"))
                .andExpect(jsonPath("$.purchase.amount").value("95.00"))
                .andExpect(jsonPath("$.purchase.currency").value("EUR"))
                .andExpect(jsonPath("$.label").value("Basse saison"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString(), "$.id"));

        api.getProduct(agency(agencyA), room)
                .andExpect(jsonPath("$.rates[*].id").value(contains(id.toString())))
                .andExpect(jsonPath("$.rates[0].purchase.amount").value("95.00"));
        // Montant exact en base : NUMERIC(19,4), sans arrondi.
        BigDecimal stored = (BigDecimal) TenantContext.callAsPlatform(() -> transaction.execute(status -> entityManager
                .createNativeQuery("SELECT purchase_amount FROM rate WHERE id = :id").setParameter("id", id)
                .getSingleResult()));
        assertThat(stored).isEqualByComparingTo("95").hasScaleOf(4);
    }

    @Test
    void anotherCurrencyIsAcceptedWhenSentExplicitly() throws Exception {
        api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "1250.5", "MAD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchase.amount").value("1250.50"))
                .andExpect(jsonPath("$.purchase.currency").value("MAD"));
        api.createRate(agency(agencyA), room, rate("SINGLE", "2027-01-01", "2027-06-30", "15000", "JPY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchase.amount").value("15000"));
        api.createRate(agency(agencyA), room, rate("TRIPLE", "2027-01-01", "2027-06-30", "250.125", "TND"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchase.amount").value("250.125"));
    }

    @Test
    void theAmountMustBeAStringWithNoMoreDecimalsThanTheCurrency() throws Exception {
        String invalid = "validation-failed";
        String body = expectProblem(api.createRate(agency(agencyA), room,
                rate("DOUBLE", "2027-01-01", "2027-06-30", "95.555", null)), 400, invalid);
        assertThat(body).contains("TOO_MANY_DECIMALS").doesNotContain("95.555");
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95.555", "EUR")),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95.5", "JPY")),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "0.00", null)),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "-10", null)),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "1e3", null)),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room,
                rate("DOUBLE", "2027-01-01", "2027-06-30", "1000000000000000", null)), 400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", "eur")),
                400, invalid);
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", "ZZZ")),
                400, invalid);
        // Nombre JSON : refuse, jamais converti (corps illisible).
        expectProblem(api.createRateRaw(agency(agencyA), room, """
                {"basis": "DOUBLE", "validFrom": "2027-01-01", "validTo": "2027-06-30",
                 "purchase": {"amount": 95.5, "currency": "EUR"}}
                """), 400, "malformed-request");
        api.listRates(agency(agencyA), room).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void theAmountErrorIsTranslated() throws Exception {
        api.mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(SupplierApi.rates(room))
                        .with(agency(agencyA)).header("Accept-Language", "en")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(SupplierApi.json(rate("DOUBLE", "2027-01-01", "2027-06-30", "95.555", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid data"))
                .andExpect(jsonPath("$.reason").value("TOO_MANY_DECIMALS"));
    }

    @Test
    void thePeriodMustEndOnOrAfterItsStart() throws Exception {
        expectProblem(api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-06-30", "2027-01-01", "95", null)),
                400, "validation-failed");
        api.createRate(agency(agencyA), room, rate("DOUBLE", "2027-03-01", "2027-03-01", "95", null))
                .andExpect(status().isCreated());
        // Une periode passee est acceptee (saisie d'historique).
        api.createRate(agency(agencyA), room, rate("SINGLE", "2020-01-01", "2020-12-31", "80", null))
                .andExpect(status().isCreated());
        var missing = rate("DOUBLE", "2027-04-01", "2027-04-30", "95", null);
        missing.remove("validTo");
        expectProblem(api.createRate(agency(agencyA), room, missing), 400, "validation-failed");
        expectProblem(api.createRate(agency(agencyA), room, rate("SUITE", "2027-04-01", "2027-04-30", "95", null)),
                400, "malformed-request");
    }

    @Test
    void filtersByDayBasisAndArchivedAndSortsByBasisThenStart() throws Exception {
        UUID doubleSummer = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-07-01", "2027-12-31", "120", null));
        UUID doubleWinter = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));
        UUID single = api.createRateOk(agency(agencyA), room, rate("SINGLE", "2027-01-01", "2027-12-31", "80", null));
        UUID archived = api.createRateOk(agency(agencyA), room, rate("PER_PERSON", "2027-01-01", "2027-12-31", "50", null));
        api.archiveRate(agency(agencyA), room, archived).andExpect(status().isOk());

        api.listRates(agency(agencyA), room).andExpect(jsonPath("$[*].id").value(contains(single.toString(),
                doubleWinter.toString(), doubleSummer.toString())));
        api.listRates(agency(agencyA), room, "on", "2027-06-30")
                .andExpect(jsonPath("$[*].id").value(contains(single.toString(), doubleWinter.toString())));
        api.listRates(agency(agencyA), room, "on", "2027-07-01", "basis", "DOUBLE")
                .andExpect(jsonPath("$[*].id").value(contains(doubleSummer.toString())));
        api.listRates(agency(agencyA), room, "archived", "true")
                .andExpect(jsonPath("$[*].id").value(contains(archived.toString())));
        api.getProduct(agency(agencyA), room).andExpect(jsonPath("$.rates[*].id").value(contains(single.toString(),
                doubleWinter.toString(), doubleSummer.toString())));
        api.listRates(agency(agencyA), room, "on", "30/06/2027").andExpect(status().isBadRequest());
    }

    @Test
    void changingTheSupplierDefaultCurrencyDoesNotChangeExistingRates() throws Exception {
        UUID id = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));

        long version = SupplierApi.version(api.getSupplier(agency(agencyA), riad));
        api.updateSupplier(agency(agencyA), riad, version,
                        with(supplier("RIAD", "Riad Atlas Test"), "defaultCurrency", "MAD"))
                .andExpect(status().isOk());

        api.listRates(agency(agencyA), room)
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].purchase.currency").value("EUR"))
                .andExpect(jsonPath("$[0].purchase.amount").value("95.00"));
        api.createRate(agency(agencyA), room, rate("SINGLE", "2027-01-01", "2027-06-30", "700", null))
                .andExpect(jsonPath("$.purchase.currency").value("MAD"));
        // Modification sans devise : celle du tarif est gardee.
        api.updateRate(agency(agencyA), room, id, 0, rate("DOUBLE", "2027-01-01", "2027-06-30", "99.90", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase.amount").value("99.90"))
                .andExpect(jsonPath("$.purchase.currency").value("EUR"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updatesARateWithItsVersion() throws Exception {
        UUID id = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));

        api.updateRate(agency(agencyA), room, id, 0,
                        with(rate("SINGLE", "2027-02-01", "2027-05-31", "70.5", "USD"), "label", "Promo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("SINGLE"))
                .andExpect(jsonPath("$.validFrom").value("2027-02-01"))
                .andExpect(jsonPath("$.purchase.amount").value("70.50"))
                .andExpect(jsonPath("$.purchase.currency").value("USD"))
                .andExpect(jsonPath("$.label").value("Promo"));
        expectProblem(api.updateRate(agency(agencyA), room, id, 0, rate("SINGLE", "2027-02-01", "2027-05-31", "70", null)),
                409, "stale-version");
        expectProblem(api.updateRate(agency(agencyA), room, id, 1, rate("SINGLE", "2027-02-01", "2027-05-31", "70.123",
                null)), 400, "validation-failed");
    }

    @Test
    void aRateOfAnArchivedProductCannotBeCreatedOrChanged() throws Exception {
        UUID id = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));
        api.archiveProduct(agency(agencyA), room).andExpect(status().isOk());

        expectProblem(api.createRate(agency(agencyA), room, rate("SINGLE", "2027-01-01", "2027-06-30", "80", null)),
                409, "product-archived");
        expectProblem(api.updateRate(agency(agencyA), room, id, 0, rate("DOUBLE", "2027-01-01", "2027-06-30", "96", null)),
                409, "product-archived");
        api.listRates(agency(agencyA), room).andExpect(jsonPath("$[*].id").value(contains(id.toString())));
    }

    @Test
    void aRateOfAnotherProductOrAgencyIsNotFound() throws Exception {
        UUID id = api.createRateOk(agency(agencyA), room, rate("DOUBLE", "2027-01-01", "2027-06-30", "95", null));
        UUID otherRoom = api.createProductOk(agency(agencyA), riad, room("Suite"));
        var body = rate("DOUBLE", "2027-01-01", "2027-06-30", "96", null);

        expectProblem(api.updateRate(agency(agencyA), otherRoom, id, 0, body), 404, "not-found");
        expectProblem(api.archiveRate(agency(agencyA), otherRoom, id), 404, "not-found");
        expectProblem(api.restoreRate(agency(agencyA), otherRoom, id), 404, "not-found");
        expectProblem(api.listRates(agency(agencyB), room), 404, "not-found");
        expectProblem(api.createRate(agency(agencyB), room, body), 404, "not-found");
        expectProblem(api.updateRate(agency(agencyB), room, id, 0, body), 404, "not-found");
        expectProblem(api.archiveRate(agency(agencyB), room, id), 404, "not-found");
        api.listRates(agency(agencyA), room).andExpect(jsonPath("$[0].purchase.amount").value("95.00"));
    }
}
