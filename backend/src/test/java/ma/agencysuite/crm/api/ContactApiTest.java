package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.CrmApi.company;
import static ma.agencysuite.crm.CrmApi.contact;
import static ma.agencysuite.crm.CrmApi.individual;
import static ma.agencysuite.crm.CrmApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.infrastructure.ContactRepository;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Contacts des clients entreprises : reserve aux entreprises, un seul principal, isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContactApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @MockitoSpyBean
    private ContactRepository contactRepository;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new CrmApi(mvc);
        agencyA = data.createAgency("contact-a-" + suffix);
        agencyB = data.createAgency("contact-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void addsAContactWithNormalizedFields() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        var body = contact("  Salma Test ", true);
        body.put("jobTitle", "Acheteuse");
        body.put("email", "Salma.Test@Example.test");
        body.put("phone", "0612345678");

        var response = api.addContact(agency(agencyA), customer, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Salma Test"))
                .andExpect(jsonPath("$.jobTitle").value("Acheteuse"))
                .andExpect(jsonPath("$.email").value("salma.test@example.test"))
                .andExpect(jsonPath("$.phone").value("+212612345678"))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(CrmApi.CUSTOMERS + "/" + customer + "/contacts/" + id);
        api.get(agency(agencyA), customer).andExpect(jsonPath("$.contacts[0].id").value(id));
        api.expectProblem(api.addContact(agency(agencyA), customer, with(contact("Invalide", false), "phone", "123")),
                400, "validation-failed");
        api.expectProblem(api.addContact(agency(agencyA), customer, contact(" ", false)), 400, "validation-failed");
    }

    @Test
    void contactsAreForCompaniesOnly() throws Exception {
        UUID individual = api.createOk(agency(agencyA), individual("Karim Test"));

        api.expectProblem(api.addContact(agency(agencyA), individual, contact("Contact Test", false)), 409,
                "contacts-company-only");
        api.get(agency(agencyA), individual).andExpect(jsonPath("$.contacts").isEmpty());
    }

    @Test
    void thereIsOnlyOnePrimaryContact() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID first = api.addContactOk(agency(agencyA), customer, "Amine Test", true);
        UUID second = api.addContactOk(agency(agencyA), customer, "Badr Test", true);

        api.get(agency(agencyA), customer)
                .andExpect(jsonPath("$.contacts[*].fullName").value(contains("Badr Test", "Amine Test")))
                .andExpect(jsonPath("$.contacts[*].isPrimary").value(contains(true, false)));

        long firstVersion = contactVersion(customer, first);
        updateContact(customer, first, firstVersion, contact("Amine Test", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));
        api.get(agency(agencyA), customer)
                .andExpect(jsonPath("$.contacts[*].id").value(contains(first.toString(), second.toString())))
                .andExpect(jsonPath("$.contacts[*].isPrimary").value(contains(true, false)));

        api.addContactOk(agency(agencyA), customer, "Chama Test", false);
        api.get(agency(agencyA), customer)
                .andExpect(jsonPath("$.contacts[?(@.isPrimary == true)].id").value(contains(first.toString())));
    }

    @Test
    void updatesAndDeletesAContact() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID contactId = api.addContactOk(agency(agencyA), customer, "Amine Test", false);

        updateContact(customer, contactId, 0, with(contact("Amine Modifie Test", false), "jobTitle", "Directeur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Amine Modifie Test"))
                .andExpect(jsonPath("$.jobTitle").value("Directeur"))
                .andExpect(jsonPath("$.version").value(1));
        api.expectProblem(updateContact(customer, contactId, 0, contact("Perime", false)), 409, "stale-version");
        api.expectProblem(api.perform(MockMvcRequestBuilders
                .put(CrmApi.CUSTOMERS + "/" + customer + "/contacts/" + contactId).with(agency(agencyA))
                .contentType(MediaType.APPLICATION_JSON).content(CrmApi.json(contact("Sans Version", false)))),
                400, "validation-failed");

        deleteContact(agencyA, customer, contactId).andExpect(status().isNoContent());
        api.get(agency(agencyA), customer).andExpect(jsonPath("$.contacts").isEmpty());
        api.expectProblem(deleteContact(agencyA, customer, contactId), 404, "not-found");
    }

    @Test
    void aCompanyWithContactsCannotBecomeAnIndividual() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID contactId = api.addContactOk(agency(agencyA), customer, "Amine Test", true);

        api.expectProblem(api.update(agency(agencyA), customer, 0, individual("Atlas Pharma Test")), 409,
                "customer-has-contacts");
        api.get(agency(agencyA), customer).andExpect(jsonPath("$.type").value("COMPANY"));

        deleteContact(agencyA, customer, contactId).andExpect(status().isNoContent());
        api.update(agency(agencyA), customer, 0, individual("Atlas Pharma Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"));
    }

    @Test
    void aContactOfAnotherCustomerIs404() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID other = api.createOk(agency(agencyA), company("Autre Societe Test"));
        UUID contactId = api.addContactOk(agency(agencyA), customer, "Amine Test", false);

        api.expectProblem(updateContact(other, contactId, 0, contact("Pirate", false)), 404, "not-found");
        api.expectProblem(deleteContact(agencyA, other, contactId), 404, "not-found");
        api.get(agency(agencyA), customer).andExpect(jsonPath("$.contacts[0].fullName").value("Amine Test"));
    }

    @Test
    void aContactOfAnotherAgencyIs404() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID contactId = api.addContactOk(agency(agencyA), customer, "Amine Test", false);
        UUID customerOfB = api.createOk(agency(agencyB), company("Societe B Test"));

        api.expectProblem(api.perform(MockMvcRequestBuilders
                .put(CrmApi.CUSTOMERS + "/" + customer + "/contacts/" + contactId).with(agency(agencyB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(with(contact("Pirate", false), "version", 0)))), 404, "not-found");
        api.expectProblem(deleteContact(agencyB, customer, contactId), 404, "not-found");
        api.expectProblem(deleteContact(agencyB, customerOfB, contactId), 404, "not-found");
        api.get(agency(agencyA), customer).andExpect(jsonPath("$.contacts[0].fullName").value("Amine Test"));
    }

    /**
     * Course entre deux utilisateurs : l'autre transaction designe un principal apres notre lecture.
     * Simulee en cachant le principal en place a la lecture ; l'index unique partiel
     * uk_contact_one_primary refuse alors l'ecriture, traduite en 409 stale-version.
     */
    @Test
    void aPrimaryDesignatedConcurrentlyIsAStaleVersion() throws Exception {
        UUID customer = api.createOk(agency(agencyA), company("Atlas Pharma Test"));
        UUID first = api.addContactOk(agency(agencyA), customer, "Amine Test", true);
        UUID second = api.addContactOk(agency(agencyA), customer, "Badr Test", false);
        doReturn(Optional.empty()).when(contactRepository).findByCustomerIdAndPrimaryTrue(customer);

        api.expectProblem(api.addContact(agency(agencyA), customer, contact("Chama Test", true)), 409,
                "stale-version");
        api.expectProblem(updateContact(customer, second, 0, contact("Badr Test", true)), 409, "stale-version");

        api.get(agency(agencyA), customer)
                .andExpect(jsonPath("$.contacts[*].fullName").value(contains("Amine Test", "Badr Test")))
                .andExpect(jsonPath("$.contacts[?(@.isPrimary == true)].id").value(contains(first.toString())))
                .andExpect(jsonPath("$.contacts[?(@.id == '%s')].version".formatted(second)).value(contains(0)));
    }

    private ResultActions updateContact(UUID customer, UUID contactId, long version, Map<String, Object> body)
            throws Exception {
        return api.perform(MockMvcRequestBuilders.put(CrmApi.CUSTOMERS + "/" + customer + "/contacts/" + contactId)
                .with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                .content(CrmApi.json(with(body, "version", version))));
    }

    private ResultActions deleteContact(UUID agencyId, UUID customer, UUID contactId) throws Exception {
        return api.perform(MockMvcRequestBuilders.delete(CrmApi.CUSTOMERS + "/" + customer + "/contacts/" + contactId)
                .with(agency(agencyId)));
    }

    private long contactVersion(UUID customer, UUID contactId) throws Exception {
        String body = api.get(agency(agencyA), customer).andReturn().getResponse().getContentAsString();
        java.util.List<Number> versions = JsonPath.read(body, "$.contacts[?(@.id == '" + contactId + "')].version");
        return versions.getFirst().longValue();
    }
}
