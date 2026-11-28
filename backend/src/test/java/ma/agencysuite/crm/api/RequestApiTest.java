package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.RequestApi.request;
import static ma.agencysuite.crm.RequestApi.with;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.RequestApi;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
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
 * Demandes des clients (US-701) : creation, besoin exprime, budget, filtres, tri, pagination,
 * recherche, modification sans changement de statut, permissions et isolation entre agences.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private RequestApi api;
    private CrmApi customers;
    private UUID agencyA;
    private UUID agencyB;
    private UUID atlas;

    @BeforeEach
    void createAgencies() throws Exception {
        api = new RequestApi(mvc);
        customers = new CrmApi(mvc);
        agencyA = data.createAgency("req-a-" + suffix);
        agencyB = data.createAgency("req-b-" + suffix);
        atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aMinimalRequestIsNewAndOwnedByTheCurrentUser() throws Exception {
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));

        api.create(agency(agencyA), request(atlas, "PHONE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.statusChangedAt").isNotEmpty())
                .andExpect(jsonPath("$.ownerUserId").value(manager.toString()))
                .andExpect(jsonPath("$.channel").value("PHONE"))
                .andExpect(jsonPath("$.source").value(nullValue()))
                .andExpect(jsonPath("$.customer.id").value(atlas.toString()))
                .andExpect(jsonPath("$.customer.name").value("Atlas Pharma Test"))
                .andExpect(jsonPath("$.customer.type").value("COMPANY"))
                .andExpect(jsonPath("$.adults").value(1))
                .andExpect(jsonPath("$.children").value(0))
                .andExpect(jsonPath("$.budget").value(nullValue()))
                .andExpect(jsonPath("$.lostReason").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$", not(hasKey("searchKey"))));
    }

    @Test
    void theCreatedRequestHasALocationAndIsReadable() throws Exception {
        String location = api.create(agency(agencyA), request(atlas, "EMAIL"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(RequestApi.REQUESTS + "/")))
                .andReturn().getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        api.get(agency(agencyA), id).andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    void theFullNeedIsStoredAsSent() throws Exception {
        Map<String, Object> body = request(atlas, "WHATSAPP");
        body.put("source", "REFERRAL");
        body.put("destination", "  Istanbul  ");
        body.put("departureDate", "2027-07-10");
        body.put("returnDate", "2027-07-17");
        body.put("datesNote", "mi-juillet, une semaine");
        body.put("adults", 2);
        body.put("children", 1);
        body.put("budget", Map.of("amount", "25000.00"));
        body.put("description", "Circuit culturel, hotel 4 etoiles");

        UUID id = api.createOk(agency(agencyA), body);

        api.get(agency(agencyA), id)
                .andExpect(jsonPath("$.source").value("REFERRAL"))
                .andExpect(jsonPath("$.destination").value("Istanbul"))
                .andExpect(jsonPath("$.departureDate").value("2027-07-10"))
                .andExpect(jsonPath("$.returnDate").value("2027-07-17"))
                .andExpect(jsonPath("$.datesNote").value("mi-juillet, une semaine"))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.children").value(1))
                .andExpect(jsonPath("$.budget.amount").value("25000.00"))
                .andExpect(jsonPath("$.budget.currency").value("MAD"))
                .andExpect(jsonPath("$.description").value("Circuit culturel, hotel 4 etoiles"));
    }

    @Test
    void pastDatesAndSameDayReturnAreAccepted() throws Exception {
        api.create(agency(agencyA), with(with(request(atlas, "PHONE"), "departureDate", "2020-05-01"),
                "returnDate", "2020-05-01")).andExpect(status().isCreated());
    }

    @Test
    void aReturnBeforeTheDepartureIsRefused() throws Exception {
        customers.expectProblem(api.create(agency(agencyA), with(with(request(atlas, "PHONE"),
                "departureDate", "2027-07-10"), "returnDate", "2027-07-09")), 400, "validation-failed");
    }

    @Test
    void atLeastOnePersonIsRequired() throws Exception {
        Map<String, Object> nobody = with(with(request(atlas, "PHONE"), "adults", 0), "children", 0);
        customers.expectProblem(api.create(agency(agencyA), nobody), 400, "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "adults", 1000)), 400,
                "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "children", -1)), 400,
                "validation-failed");
        api.create(agency(agencyA), with(with(request(atlas, "PHONE"), "adults", 0), "children", 2))
                .andExpect(status().isCreated());
    }

    @Test
    void customerAndChannelAreRequired() throws Exception {
        customers.expectProblem(api.create(agency(agencyA), Map.of("channel", "PHONE")), 400, "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), Map.of("customerId", atlas)), 400, "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), request(atlas, "FAX")), 400, "malformed-request");
    }

    @Test
    void theBudgetIsAnExactStringInMadByDefault() throws Exception {
        UUID mad = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "budget", Map.of("amount", "25000.5")));
        api.get(agency(agencyA), mad)
                .andExpect(jsonPath("$.budget.amount").value("25000.50"))
                .andExpect(jsonPath("$.budget.currency").value("MAD"));

        UUID eur = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "budget",
                Map.of("amount", "1999.99", "currency", "EUR")));
        api.get(agency(agencyA), eur)
                .andExpect(jsonPath("$.budget.amount").value("1999.99"))
                .andExpect(jsonPath("$.budget.currency").value("EUR"));

        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "budget",
                Map.of("amount", "10.125"))), 400, "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "budget",
                Map.of("amount", "0"))), 400, "validation-failed");
        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "budget",
                Map.of("amount", 25000.5))), 400, "malformed-request");
    }

    @Test
    void theListFiltersByStatusOwnerCustomerAndChannel() throws Exception {
        UUID other = customers.createOk(agency(agencyA), CrmApi.individual("Benani Test"));
        UUID sales = iam.createUser(agencyA, "sales-" + suffix, UserStatus.ACTIVE, "SALES");
        UUID newOne = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        UUID quoting = api.createOk(agency(agencyA), request(atlas, "EMAIL"));
        api.moveTo(agency(agencyA), quoting, "QUOTING");
        UUID lost = api.createOk(agency(agencyA), with(request(other, "EMAIL"), "ownerUserId", sales));
        api.moveTo(agency(agencyA), lost, "LOST");

        api.list(agency(agencyA), "status=NEW&status=QUOTING")
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(newOne.toString(), quoting.toString())));
        api.list(agency(agencyA), "status=LOST").andExpect(jsonPath("$.items[*].id").value(contains(lost.toString())));
        api.list(agency(agencyA), "ownerUserId=" + sales)
                .andExpect(jsonPath("$.items[*].id").value(contains(lost.toString())));
        api.list(agency(agencyA), "customerId=" + other)
                .andExpect(jsonPath("$.items[*].id").value(contains(lost.toString())))
                .andExpect(jsonPath("$.items[0].customer.name").value("Benani Test"))
                .andExpect(jsonPath("$.items[0].customer.type").value("INDIVIDUAL"));
        api.list(agency(agencyA), "channel=EMAIL")
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(quoting.toString(), lost.toString())));
        api.list(agency(agencyA), "channel=EMAIL&status=QUOTING")
                .andExpect(jsonPath("$.items[*].id").value(contains(quoting.toString())));
        // Statut inconnu : 400 validation-failed (MethodArgumentTypeMismatchException traduite).
        customers.expectProblem(api.list(agency(agencyA), "status=DONE"), 400, "validation-failed");
    }

    @Test
    void anUnknownStatusInTheUrlIsATranslatedValidationErrorWithoutTheValue() throws Exception {
        customers.expectUnrecognizedParameter(agency(agencyA), RequestApi.REQUESTS, "status", "DONE");
    }

    @Test
    void theListFiltersByCreationPeriodInUtcDays() throws Exception {
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        api.list(agency(agencyA), "createdFrom=" + today + "&createdTo=" + today)
                .andExpect(jsonPath("$.items[*].id").value(contains(id.toString())));
        api.list(agency(agencyA), "createdFrom=" + today.plusDays(1)).andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyA), "createdTo=" + today.minusDays(1)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theListIsSortedAndPaginated() throws Exception {
        UUID first = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "departureDate", "2027-09-01"));
        UUID second = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "departureDate", "2027-03-01"));
        UUID third = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        api.moveTo(agency(agencyA), first, "QUOTING");

        api.list(agency(agencyA), "")
                .andExpect(jsonPath("$.items[*].id").value(contains(third.toString(), second.toString(),
                        first.toString())))
                .andExpect(jsonPath("$.size").value(25));
        api.list(agency(agencyA), "sort=-statusChangedAt")
                .andExpect(jsonPath("$.items[0].id").value(first.toString()));
        api.list(agency(agencyA), "sort=departureDate")
                .andExpect(jsonPath("$.items[*].id").value(contains(second.toString(), first.toString(),
                        third.toString())));
        api.list(agency(agencyA), "size=2&page=1")
                .andExpect(jsonPath("$.items[*].id").value(contains(first.toString())))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.list(agency(agencyA), "")
                .andExpect(jsonPath("$.items[0].adults").value(1))
                .andExpect(jsonPath("$.items[0].ownerUserId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0]", not(hasKey("description"))))
                .andExpect(jsonPath("$.items[0]", not(hasKey("budget"))));
        customers.expectProblem(api.list(agency(agencyA), "size=101"), 400, "validation-failed");
        customers.expectProblem(api.list(agency(agencyA), "sort=destination"), 400, "validation-failed");
    }

    @Test
    void qSearchesTheDestinationWithoutAccentsAndTheCustomerName() throws Exception {
        UUID other = customers.createOk(agency(agencyA), CrmApi.individual("Benani Test"));
        UUID istanbul = api.createOk(agency(agencyA), with(request(other, "PHONE"), "destination", "Istanbul"));
        UUID fes = api.createOk(agency(agencyA), with(request(other, "PHONE"), "destination", "Fès - Médina"));
        UUID ofAtlas = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "destination", "Dakhla"));

        api.search(agency(agencyA), "istanbul").andExpect(jsonPath("$.items[*].id").value(contains(istanbul.toString())));
        api.search(agency(agencyA), "FES medina").andExpect(jsonPath("$.items[*].id").value(contains(fes.toString())));
        api.search(agency(agencyA), "atlas").andExpect(jsonPath("$.items[*].id").value(contains(ofAtlas.toString())));
        api.search(agency(agencyA), "benani")
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(istanbul.toString(), fes.toString())));
        api.search(agency(agencyA), "%").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUpdateReplacesTheFieldsButNeverTheStatus() throws Exception {
        UUID other = customers.createOk(agency(agencyA), CrmApi.individual("Benani Test"));
        UUID id = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "destination", "Istanbul"));
        api.moveTo(agency(agencyA), id, "QUOTING");
        UUID owner = UUID.fromString(api.read(agency(agencyA), id, "$.ownerUserId"));

        Map<String, Object> body = with(with(with(request(other, "EMAIL"), "ownerUserId", owner), "destination",
                "Marrakech"), "status", "WON");
        api.update(agency(agencyA), id, api.version(agency(agencyA), id), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTING"))
                .andExpect(jsonPath("$.customer.id").value(other.toString()))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.destination").value("Marrakech"))
                .andExpect(jsonPath("$.adults").value(1));

        api.search(agency(agencyA), "marrakech").andExpect(jsonPath("$.totalElements").value(1));
        api.search(agency(agencyA), "istanbul").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anUpdateRequiresTheCurrentVersionAndTheOwner() throws Exception {
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        UUID owner = UUID.fromString(api.read(agency(agencyA), id, "$.ownerUserId"));
        Map<String, Object> body = with(request(atlas, "EMAIL"), "ownerUserId", owner);

        api.update(agency(agencyA), id, 0, body).andExpect(status().isOk());
        customers.expectProblem(api.update(agency(agencyA), id, 0, body), 409, "stale-version");
        customers.expectProblem(api.update(agency(agencyA), id, 1, request(atlas, "EMAIL")), 400,
                "validation-failed");
    }

    @Test
    void aWonOrLostRequestCanStillBeCorrected() throws Exception {
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        api.moveTo(agency(agencyA), id, "LOST");
        UUID owner = UUID.fromString(api.read(agency(agencyA), id, "$.ownerUserId"));

        api.update(agency(agencyA), id, api.version(agency(agencyA), id),
                        with(with(request(atlas, "PHONE"), "ownerUserId", owner), "destination", "Agadir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReason").value("PRICE"))
                .andExpect(jsonPath("$.destination").value("Agadir"));
    }

    @Test
    void anAccountantReadsButDoesNotWrite() throws Exception {
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        var accountant = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, "ACCOUNTANT"));

        api.list(accountant, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        api.get(accountant, id).andExpect(status().isOk());
        customers.expectProblem(api.create(accountant, request(atlas, "PHONE")), 403, "permission-denied");
        customers.expectProblem(api.update(accountant, id, 0, request(atlas, "PHONE")), 403, "permission-denied");
        customers.expectProblem(api.changeStatus(accountant, id, 0, "QUOTING", null, null), 403,
                "permission-denied");
    }

    @Test
    void anotherAgencyNeitherReadsNorCountsNorChangesTheRequests() throws Exception {
        UUID id = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "destination", "Istanbul"));
        UUID ofB = customers.createOk(agency(agencyB), CrmApi.company("Societe B Test"));

        customers.expectProblem(api.get(agency(agencyB), id), 404, "not-found");
        api.list(agency(agencyB), "").andExpect(jsonPath("$.totalElements").value(0));
        api.search(agency(agencyB), "istanbul").andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyB), "customerId=" + atlas).andExpect(jsonPath("$.totalElements").value(0));
        UUID managerOfB = iam.userId(agencyB, TestTokens.managerSub(agencyB));
        customers.expectProblem(api.update(agency(agencyB), id, 0,
                with(request(ofB, "PHONE"), "ownerUserId", managerOfB)), 404, "not-found");
        customers.expectProblem(api.changeStatus(agency(agencyB), id, 0, "QUOTING", null, null), 404, "not-found");

        api.get(agency(agencyA), id).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void thereIsNoDeleteEndpoint() throws Exception {
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));

        api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(RequestApi.REQUESTS + "/" + id).with(agency(agencyA)))
                .andExpect(status().is4xxClientError());
        api.get(agency(agencyA), id).andExpect(status().isOk());
    }
}
