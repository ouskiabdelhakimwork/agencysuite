package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.CrmApi.company;
import static ma.agencysuite.crm.CrmApi.individual;
import static ma.agencysuite.crm.CrmApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
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
 * API des clients (/api/v1/customers) : creation, normalisation, validation, recherche,
 * pagination, permissions et isolation entre agences (US-401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerApiTest {

    private static final String ICE = "001234567000089";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new CrmApi(mvc);
        agencyA = data.createAgency("crm-a-" + suffix);
        agencyB = data.createAgency("crm-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createsAnIndividualWithDefaults() throws Exception {
        var response = api.create(agency(agencyA), individual("  Karim Test  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.name").value("Karim Test"))
                .andExpect(jsonPath("$.country").value("MA"))
                .andExpect(jsonPath("$.preferredLocale").value("fr"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.contacts").isEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(CrmApi.CUSTOMERS + "/" + id);
        api.get(agency(agencyA), UUID.fromString(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Karim Test"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createsACompanyWithItsLegalIdentifiersAndAllFields() throws Exception {
        var body = company("Atlas Pharma Test");
        body.put("ice", ICE);
        body.put("ifNumber", "12345678");
        body.put("rc", "RC-CASA-1234");
        body.put("addressLine", "1 rue de Test");
        body.put("city", "Casablanca");
        body.put("country", "FR");
        body.put("preferredLocale", "ar");
        body.put("sector", "Pharmacie");
        body.put("source", "TRADE_SHOW");
        body.put("notes", "Client de test");

        api.create(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(CrmApi.CUSTOMERS + "/")))
                .andExpect(jsonPath("$.type").value("COMPANY"))
                .andExpect(jsonPath("$.ice").value(ICE))
                .andExpect(jsonPath("$.ifNumber").value("12345678"))
                .andExpect(jsonPath("$.rc").value("RC-CASA-1234"))
                .andExpect(jsonPath("$.city").value("Casablanca"))
                .andExpect(jsonPath("$.country").value("FR"))
                .andExpect(jsonPath("$.preferredLocale").value("ar"))
                .andExpect(jsonPath("$.source").value("TRADE_SHOW"))
                .andExpect(jsonPath("$.ownerUserId").value(nullValue()));
    }

    @Test
    void legalIdentifiersAreRefusedForAnIndividual() throws Exception {
        api.expectProblem(api.create(agency(agencyA), with(individual("Karim Test"), "ice", ICE)), 400,
                "validation-failed");
        api.expectProblem(api.create(agency(agencyA), with(individual("Karim Test"), "rc", "RC-1")), 400,
                "validation-failed");
        api.create(agency(agencyA), with(individual("Karim Test"), "ice", "  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ice").value(nullValue()));
    }

    @Test
    void phoneIsStoredInE164AndEmailInLowerCase() throws Exception {
        var body = with(individual("Karim Test"), "phone", "06 12 34 56 78");
        body.put("email", "  Karim.Test@Example.TEST ");

        api.create(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+212612345678"))
                .andExpect(jsonPath("$.email").value("karim.test@example.test"));
        api.create(agency(agencyA), with(individual("Autre Test"), "phone", "+33 1 23 45 67 89"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+33123456789"));
    }

    @Test
    void invalidFieldsAre400() throws Exception {
        List<CrmApiCase> cases = List.of(
                new CrmApiCase("phone", "12345"),
                new CrmApiCase("phone", "pas un numero"),
                new CrmApiCase("email", "pas-un-email"),
                new CrmApiCase("ice", "12345"),
                new CrmApiCase("country", "ZZ"),
                new CrmApiCase("country", "ma"),
                new CrmApiCase("preferredLocale", "de"),
                new CrmApiCase("name", " "),
                new CrmApiCase("name", "N".repeat(201)));
        for (CrmApiCase invalid : cases) {
            api.expectProblem(api.create(agency(agencyA), with(company("Societe Test"), invalid.field(),
                    invalid.value())), 400, "validation-failed");
        }
        api.expectProblem(api.create(agency(agencyA), with(company("Societe Test"), "source", "PIGEON")), 400,
                "malformed-request");
        api.expectProblem(api.create(agency(agencyA), with(company("Societe Test"), "type", null)), 400,
                "validation-failed");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listIsPaginatedAndSorted() throws Exception {
        api.createOk(agency(agencyA), individual("bravo Test"));
        api.createOk(agency(agencyA), individual("Alpha Test"));
        api.createOk(agency(agencyA), individual("Charlie Test"));

        api.list(agency(agencyA), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(
                        org.hamcrest.Matchers.contains("Alpha Test", "bravo Test", "Charlie Test")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
        api.list(agency(agencyA), "size=2&page=1")
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Charlie Test")))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.list(agency(agencyA), "sort=-createdAt")
                .andExpect(jsonPath("$.items[0].name").value("Charlie Test"));
        api.list(agency(agencyA), "sort=-updatedAt")
                .andExpect(jsonPath("$.items[0].name").value("Charlie Test"));

        api.list(agency(agencyA), "size=101").andExpect(status().isBadRequest());
        api.list(agency(agencyA), "size=0").andExpect(status().isBadRequest());
        api.list(agency(agencyA), "page=-1").andExpect(status().isBadRequest());
        api.list(agency(agencyA), "sort=email").andExpect(status().isBadRequest());
    }

    @Test
    void listItemsHaveTheSummaryFields() throws Exception {
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));
        var body = with(company("Atlas Pharma Test"), "ownerUserId", manager);
        body.put("email", "contact@atlas.example.test");
        body.put("phone", "0522000000");
        body.put("city", "Casablanca");
        UUID id = api.createOk(agency(agencyA), body);

        api.list(agency(agencyA), "")
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].type").value("COMPANY"))
                .andExpect(jsonPath("$.items[0].name").value("Atlas Pharma Test"))
                .andExpect(jsonPath("$.items[0].email").value("contact@atlas.example.test"))
                .andExpect(jsonPath("$.items[0].phone").value("+212522000000"))
                .andExpect(jsonPath("$.items[0].city").value("Casablanca"))
                .andExpect(jsonPath("$.items[0].ownerUserId").value(manager.toString()))
                .andExpect(jsonPath("$.items[0].archived").value(false))
                .andExpect(jsonPath("$.items[0].ice").doesNotExist());
    }

    @Test
    void filtersByTypeOwnerAndArchived() throws Exception {
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));
        api.createOk(agency(agencyA), individual("Particulier Test"));
        api.createOk(agency(agencyA), with(company("Entreprise Test"), "ownerUserId", manager));
        UUID archived = api.createOk(agency(agencyA), company("Archivee Test"));
        api.archive(agency(agencyA), archived).andExpect(status().isOk());

        api.list(agency(agencyA), "type=COMPANY")
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Entreprise Test")));
        api.list(agency(agencyA), "type=INDIVIDUAL")
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Particulier Test")));
        api.list(agency(agencyA), "ownerUserId=" + manager)
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Entreprise Test")));
        api.list(agency(agencyA), "archived=true")
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Archivee Test")));
        api.expectProblem(api.list(agency(agencyA), "type=PIGEON"), 400, "validation-failed");
    }

    @Test
    void anUnknownTypeInTheUrlIsATranslatedValidationErrorWithoutTheValue() throws Exception {
        api.expectUnrecognizedParameter(agency(agencyA), CrmApi.CUSTOMERS, "type", "FOO");
    }

    @Test
    void searchesNameEmailPhoneAndIceIgnoringCase() throws Exception {
        var atlas = company("Atlas Pharma Test");
        atlas.put("email", "contact@atlas.example.test");
        atlas.put("phone", "0522000000");
        atlas.put("ice", ICE);
        api.createOk(agency(agencyA), atlas);
        api.createOk(agency(agencyA), with(individual("Karim Test"), "email", "karim@example.test"));

        for (String q : List.of("atlas", "PHARMA", "CONTACT@ATLAS", "0522000000", "+212522000000", "522000",
                "4567000", "05 22 00 00 00")) {
            api.search(agency(agencyA), q)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Atlas Pharma Test")));
        }
        api.search(agency(agencyA), "example.test").andExpect(jsonPath("$.totalElements").value(2));
        api.search(agency(agencyA), "_").andExpect(jsonPath("$.totalElements").value(0));
        api.search(agency(agencyA), "%").andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyA), "q=introuvable").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void accountantReadsButCannotWrite() throws Exception {
        UUID id = api.createOk(agency(agencyA), company("Societe Test"));
        var accountant = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, "ACCOUNTANT"));

        api.list(accountant, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        api.get(accountant, id).andExpect(status().isOk());
        api.expectProblem(api.create(accountant, company("Autre Test")), 403, "permission-denied");
        api.expectProblem(api.update(accountant, id, 0, company("Renommee Test")), 403, "permission-denied");
        api.expectProblem(api.archive(accountant, id), 403, "permission-denied");
        api.expectProblem(api.addContact(accountant, id, CrmApi.contact("Contact Test", false)), 403,
                "permission-denied");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void salesCanWrite() throws Exception {
        var sales = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, "SALES"));

        api.create(sales, company("Societe Test")).andExpect(status().isCreated());
    }

    @Test
    void ownerMustBeAUserOfTheAgency() throws Exception {
        UUID managerOfB = iam.userId(agencyB, TestTokens.managerSub(agencyB));

        api.expectProblem(api.create(agency(agencyA), with(company("Societe Test"), "ownerUserId", managerOfB)), 400,
                "invalid-owner");
        api.expectProblem(api.create(agency(agencyA), with(company("Societe Test"), "ownerUserId",
                UUID.randomUUID())), 400, "invalid-owner");
        UUID id = api.createOk(agency(agencyA), company("Societe Test"));
        api.expectProblem(api.update(agency(agencyA), id, 0, with(company("Societe Test"), "ownerUserId",
                managerOfB)), 400, "invalid-owner");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anotherAgencyNeitherSeesNorChangesTheCustomer() throws Exception {
        UUID id = api.createOk(agency(agencyA), with(company("Societe A Test"), "email", "a@example.test"));

        api.expectProblem(api.get(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.update(agency(agencyB), id, 0, company("Pirate")), 404, "not-found");
        api.expectProblem(api.archive(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.restore(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.addContact(agency(agencyB), id, CrmApi.contact("Pirate", true)), 404, "not-found");
        api.list(agency(agencyB), "").andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyB), "q=example.test").andExpect(jsonPath("$.totalElements").value(0));

        api.get(agency(agencyA), id)
                .andExpect(jsonPath("$.name").value("Societe A Test"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.contacts").isEmpty());
    }

    @Test
    void unknownCustomerIs404AndBodyHasNoInternalDetails() throws Exception {
        String body = api.expectProblem(api.get(agency(agencyA), UUID.randomUUID()), 404, "not-found");

        assertThat(body).doesNotContain("Client introuvable").doesNotContain("Exception");
    }

    @Test
    void updateReplacesTheEditableFields() throws Exception {
        var body = company("Societe Test");
        body.put("ice", ICE);
        body.put("city", "Rabat");
        UUID id = api.createOk(agency(agencyA), body);

        api.update(agency(agencyA), id, 0, with(company("Societe Renommee Test"), "phone", "0612345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Societe Renommee Test"))
                .andExpect(jsonPath("$.phone").value("+212612345678"))
                .andExpect(jsonPath("$.ice").value(nullValue()))
                .andExpect(jsonPath("$.city").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));
        api.expectProblem(api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put(CrmApi.CUSTOMERS + "/" + id).with(agency(agencyA))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(CrmApi.json(company("Sans Version")))), 400, "validation-failed");
    }

    @Test
    void becomingAnIndividualClearsTheLegalIdentifiers() throws Exception {
        var body = company("Societe Test");
        body.put("ice", ICE);
        body.put("rc", "RC-1");
        UUID id = api.createOk(agency(agencyA), body);

        var asIndividual = individual("Societe Test");
        asIndividual.put("ice", ICE);
        api.update(agency(agencyA), id, 0, asIndividual)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.ice").value(nullValue()))
                .andExpect(jsonPath("$.rc").value(nullValue()));
    }

    @Test
    void endpointsAreDocumentedInOpenApi() throws Exception {
        api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/restore'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/contacts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/contacts/{contactId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/contacts/{contactId}'].delete").exists());
    }

    private record CrmApiCase(String field, Object value) {
    }

}
