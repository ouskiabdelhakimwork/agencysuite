package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static ma.agencysuite.crm.TravelerApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.TravelerApi;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * API des voyageurs (/api/v1/travelers) : creation, normalisation, validation, recherche (accents,
 * arabe, e-mail, telephone), pagination, tri, filtres, permissions et isolation entre agences.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private CrmApi customers;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new TravelerApi(mvc);
        customers = new CrmApi(mvc);
        agencyA = data.createAgency("trv-a-" + suffix);
        agencyB = data.createAgency("trv-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createsAMinimalTraveler() throws Exception {
        var response = api.create(agency(agencyA), traveler("  Bennani ", " Salma  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value("Bennani"))
                .andExpect(jsonPath("$.firstName").value("Salma"))
                .andExpect(jsonPath("$.lastNameAr").value(nullValue()))
                .andExpect(jsonPath("$.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.birthDateHidden").value(false))
                .andExpect(jsonPath("$.nationality").value(nullValue()))
                .andExpect(jsonPath("$.customers").isEmpty())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.nameKey").doesNotExist())
                .andExpect(jsonPath("$.anonymizedAt").doesNotExist())
                .andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(TravelerApi.TRAVELERS + "/" + id);
        api.get(agency(agencyA), UUID.fromString(id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Salma"));
    }

    @Test
    void storesArabicNamesAndAllFields() throws Exception {
        var body = traveler("El Fassi", "Zineb");
        body.put("lastNameAr", " الفاسي ");
        body.put("firstNameAr", "زينب");
        body.put("gender", "FEMALE");
        body.put("birthDate", "1990-05-14");
        body.put("birthPlace", "Fes");
        body.put("nationality", "MA");
        body.put("dietary", "Vegetarien");
        body.put("emergencyContactName", "Contact Test");
        body.put("notes", "Voyageur de test");

        api.create(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastNameAr").value("الفاسي"))
                .andExpect(jsonPath("$.firstNameAr").value("زينب"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"))
                .andExpect(jsonPath("$.birthPlace").value("Fes"))
                .andExpect(jsonPath("$.nationality").value("MA"))
                .andExpect(jsonPath("$.dietary").value("Vegetarien"))
                .andExpect(jsonPath("$.emergencyContactName").value("Contact Test"))
                .andExpect(jsonPath("$.notes").value("Voyageur de test"));
    }

    @Test
    void emailIsLowerCaseAndPhonesAreE164() throws Exception {
        var body = traveler("Alaoui", "Omar");
        body.put("email", "  Omar.Alaoui@Example.TEST ");
        body.put("phone", "06 12 34 56 78");
        body.put("emergencyContactPhone", "0522000000");

        api.create(agency(agencyA), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("omar.alaoui@example.test"))
                .andExpect(jsonPath("$.phone").value("+212612345678"))
                .andExpect(jsonPath("$.emergencyContactPhone").value("+212522000000"));
    }

    @Test
    void invalidFieldsAre400() throws Exception {
        List<Object[]> cases = List.of(
                new Object[] {"nationality", "ZZ"},
                new Object[] {"nationality", "ma"},
                new Object[] {"nationality", "MAR"},
                new Object[] {"birthDate", LocalDate.now().plusDays(1).toString()},
                new Object[] {"birthDate", "1900-01-01"},
                new Object[] {"phone", "12345"},
                new Object[] {"emergencyContactPhone", "pas un numero"},
                new Object[] {"email", "pas-un-email"},
                new Object[] {"lastName", " "},
                new Object[] {"firstName", null},
                new Object[] {"lastName", "N".repeat(101)},
                new Object[] {"lastNameAr", "ن".repeat(101)});
        for (Object[] invalid : cases) {
            api.expectProblem(api.create(agency(agencyA), with(traveler("Test", "Invalide"), (String) invalid[0],
                    invalid[1])), 400, "validation-failed");
        }
        api.expectProblem(api.create(agency(agencyA), with(traveler("Test", "Invalide"), "gender", "AUTRE")), 400,
                "malformed-request");
        api.create(agency(agencyA), with(traveler("Test", "Aujourdhui"), "birthDate", LocalDate.now().toString()))
                .andExpect(status().isCreated());
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listIsPaginatedSortedAndHasNoBirthDate() throws Exception {
        api.createOk(agency(agencyA), with(traveler("bravo", "Test"), "birthDate", "1985-01-01"));
        api.createOk(agency(agencyA), traveler("Élan", "Test"));
        api.createOk(agency(agencyA), traveler("Charlie", "Test"));

        api.list(agency(agencyA), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].lastName").value(contains("bravo", "Charlie", "Élan")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$.items[0].birthDateHidden").doesNotExist())
                .andExpect(jsonPath("$.items[0].nameKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].archived").value(false));
        api.list(agency(agencyA), "size=2&page=1")
                .andExpect(jsonPath("$.items[*].lastName").value(contains("Élan")))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.list(agency(agencyA), "sort=-createdAt").andExpect(jsonPath("$.items[0].lastName").value("Charlie"));
        api.list(agency(agencyA), "sort=-updatedAt").andExpect(jsonPath("$.items[0].lastName").value("Charlie"));

        api.list(agency(agencyA), "size=101").andExpect(status().isBadRequest());
        api.list(agency(agencyA), "size=0").andExpect(status().isBadRequest());
        api.list(agency(agencyA), "sort=birthDate").andExpect(status().isBadRequest());
    }

    @Test
    void listItemsHaveTheSummaryFields() throws Exception {
        var body = traveler("El Fassi", "Zineb");
        body.put("lastNameAr", "الفاسي");
        body.put("firstNameAr", "زينب");
        body.put("nationality", "MA");
        body.put("email", "zineb@example.test");
        body.put("phone", "0612345678");
        UUID id = api.createOk(agency(agencyA), body);

        api.list(agency(agencyA), "")
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].lastName").value("El Fassi"))
                .andExpect(jsonPath("$.items[0].firstName").value("Zineb"))
                .andExpect(jsonPath("$.items[0].lastNameAr").value("الفاسي"))
                .andExpect(jsonPath("$.items[0].firstNameAr").value("زينب"))
                .andExpect(jsonPath("$.items[0].nationality").value("MA"))
                .andExpect(jsonPath("$.items[0].email").value("zineb@example.test"))
                .andExpect(jsonPath("$.items[0].phone").value("+212612345678"));
    }

    @Test
    void filtersByArchivedAndCustomer() throws Exception {
        UUID customer = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        api.createOk(agency(agencyA), with(traveler("Lie", "Test"), "customerIds", List.of(customer)));
        api.createOk(agency(agencyA), traveler("Libre", "Test"));
        UUID archived = api.createOk(agency(agencyA), traveler("Archive", "Test"));
        api.archive(agency(agencyA), archived).andExpect(status().isOk());

        api.list(agency(agencyA), "").andExpect(jsonPath("$.items[*].lastName").value(contains("Libre", "Lie")));
        api.list(agency(agencyA), "archived=true")
                .andExpect(jsonPath("$.items[*].lastName").value(contains("Archive")))
                .andExpect(jsonPath("$.items[0].archived").value(true));
        api.list(agency(agencyA), "customerId=" + customer)
                .andExpect(jsonPath("$.items[*].lastName").value(contains("Lie")));
        api.list(agency(agencyA), "customerId=" + UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyA), "customerId=pas-un-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void searchIgnoresCaseAndAccentsAndCoversArabicEmailAndPhone() throws Exception {
        var body = traveler("Él Fassi", "Zineb");
        body.put("lastNameAr", "الفاسي");
        body.put("firstNameAr", "زينب");
        body.put("email", "zineb.test@example.test");
        body.put("phone", "0612345678");
        api.createOk(agency(agencyA), body);
        api.createOk(agency(agencyA), with(traveler("Tazi", "Karim"), "email", "karim@example.test"));

        for (String q : List.of("fassi", "EL FASSI", "él fassi", "el   fassi zin", "Él Fassi", "الفاسي", "زينب",
                "ZINEB.TEST@", "0612345678", "+212612345678", "06 12 34 56 78", "612345")) {
            api.search(agency(agencyA), q)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].lastName").value(contains("Él Fassi")));
        }
        api.search(agency(agencyA), "example.test").andExpect(jsonPath("$.totalElements").value(2));
        api.search(agency(agencyA), "introuvable").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchTreatsTypographicAndStraightApostrophesAlike() throws Exception {
        api.createOk(agency(agencyA), traveler("Aït M’Barek", "Salma"));
        api.createOk(agency(agencyA), traveler("Ait M'Barek", "Omar"));

        for (String q : List.of("m'barek", "M’Barek", "ait mʼbarek", "Aït M‘BAREK")) {
            api.search(agency(agencyA), q).andExpect(jsonPath("$.totalElements").value(2));
        }
        api.search(agency(agencyA), "m’barek salma")
                .andExpect(jsonPath("$.items[*].lastName").value(contains("Aït M’Barek")));
    }

    @Test
    void likeWildcardsAreTakenLiterally() throws Exception {
        api.createOk(agency(agencyA), traveler("Cent%Test", "Pourcent"));
        api.createOk(agency(agencyA), traveler("CentXTest", "Autre"));

        api.search(agency(agencyA), "t%t").andExpect(jsonPath("$.items[*].lastName").value(contains("Cent%Test")));
        api.search(agency(agencyA), "%").andExpect(jsonPath("$.totalElements").value(1));
        api.search(agency(agencyA), "_").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void accountantReadsButCannotWrite() throws Exception {
        UUID id = api.createOk(agency(agencyA), traveler("Lecture", "Test"));
        UUID customer = customers.createOk(agency(agencyA), CrmApi.company("Societe Test"));
        var accountant = agencyUser(agencyA, iam.createUser(agencyA, UserStatus.ACTIVE, "ACCOUNTANT"));

        api.list(accountant, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        api.get(accountant, id).andExpect(status().isOk());
        api.expectProblem(api.create(accountant, traveler("Autre", "Test")), 403, "permission-denied");
        api.expectProblem(api.update(accountant, id, 0, traveler("Renomme", "Test")), 403, "permission-denied");
        api.expectProblem(api.archive(accountant, id), 403, "permission-denied");
        api.expectProblem(api.restore(accountant, id), 403, "permission-denied");
        api.expectProblem(api.link(accountant, id, customer), 403, "permission-denied");
        api.expectProblem(api.unlink(accountant, id, customer), 403, "permission-denied");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anotherAgencyNeitherSeesNorChangesTheTraveler() throws Exception {
        UUID id = api.createOk(agency(agencyA), with(traveler("Agence A", "Test"), "email", "a@example.test"));
        UUID customerOfB = customers.createOk(agency(agencyB), CrmApi.company("Societe B Test"));

        api.expectProblem(api.get(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.update(agency(agencyB), id, 0, traveler("Pirate", "Test")), 404, "not-found");
        api.expectProblem(api.archive(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.restore(agency(agencyB), id), 404, "not-found");
        api.expectProblem(api.link(agency(agencyB), id, customerOfB), 404, "not-found");
        api.expectProblem(api.unlink(agency(agencyB), id, customerOfB), 404, "not-found");
        api.list(agency(agencyB), "").andExpect(jsonPath("$.totalElements").value(0));
        api.list(agency(agencyB), "q=example.test").andExpect(jsonPath("$.totalElements").value(0));

        api.get(agency(agencyA), id)
                .andExpect(jsonPath("$.lastName").value("Agence A"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.customers").isEmpty());
    }

    @Test
    void unknownTravelerIs404WithoutInternalDetails() throws Exception {
        String body = api.expectProblem(api.get(agency(agencyA), UUID.randomUUID()), 404, "not-found");

        assertThat(body).doesNotContain("Voyageur introuvable").doesNotContain("Exception");
    }

    @Test
    void updateReplacesTheEditableFieldsButNotTheLinks() throws Exception {
        UUID customer = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        var body = traveler("Bennani", "Salma");
        body.put("dietary", "Sans gluten");
        body.put("customerIds", List.of(customer));
        UUID id = api.createOk(agency(agencyA), body);

        api.update(agency(agencyA), id, 0, with(traveler("Bennani", "Salma Test"), "phone", "0612345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Salma Test"))
                .andExpect(jsonPath("$.phone").value("+212612345678"))
                .andExpect(jsonPath("$.dietary").value(nullValue()))
                .andExpect(jsonPath("$.customers[*].name").value(contains("Atlas Pharma Test")))
                .andExpect(jsonPath("$.version").value(1));
        api.search(agency(agencyA), "salma test").andExpect(jsonPath("$.totalElements").value(1));
        api.expectProblem(customers.perform(MockMvcRequestBuilders.put(TravelerApi.TRAVELERS + "/" + id)
                .with(agency(agencyA)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(CrmApi.json(traveler("Sans", "Version")))), 400, "validation-failed");
    }

    @Test
    void endpointsAreDocumentedInOpenApi() throws Exception {
        String docs = customers.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/travelers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}/archive'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}/restore'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}/customers/{customerId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers/{id}/customers/{customerId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/travelers'].post.tags[0]").value("travelers"))
                .andReturn().getResponse().getContentAsString();

        String post = JsonPath.read(docs, "$.paths['/api/v1/travelers'].post.description");
        String put = JsonPath.read(docs, "$.paths['/api/v1/travelers/{id}'].put.description");
        String get = JsonPath.read(docs, "$.paths['/api/v1/travelers/{id}'].get.description");
        assertThat(List.of(post, put, get)).allSatisfy(text -> assertThat(text).contains("identity.read")
                .contains("birthPlace"));
    }
}
