package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.RequestApi.request;
import static ma.agencysuite.crm.RequestApi.with;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.RequestApi;
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
 * Client d'une demande : obligatoire, connu dans l'agence, non archive a la creation et au
 * changement de client ; une demande existante garde son client archive ensuite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestCustomerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private RequestApi api;
    private CrmApi customers;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new RequestApi(mvc);
        customers = new CrmApi(mvc);
        agencyA = data.createAgency("reqc-a-" + suffix);
        agencyB = data.createAgency("reqc-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void anUnknownCustomerOrACustomerOfAnotherAgencyIsNotFound() throws Exception {
        UUID ofB = customers.createOk(agency(agencyB), CrmApi.company("Societe B Test"));

        customers.expectProblem(api.create(agency(agencyA), request(UUID.randomUUID(), "PHONE")), 404, "not-found");
        customers.expectProblem(api.create(agency(agencyA), request(ofB, "PHONE")), 404, "not-found");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void changingToAnUnknownCustomerOrOneOfAnotherAgencyIsNotFound() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID ofB = customers.createOk(agency(agencyB), CrmApi.company("Societe B Test"));
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));

        customers.expectProblem(api.update(agency(agencyA), id, 0, body(ofB, id)), 404, "not-found");
        customers.expectProblem(api.update(agency(agencyA), id, 0, body(UUID.randomUUID(), id)), 404, "not-found");
        api.get(agency(agencyA), id).andExpect(jsonPath("$.customer.id").value(atlas.toString()));
    }

    @Test
    void anArchivedCustomerCannotReceiveANewRequest() throws Exception {
        UUID archived = customers.createOk(agency(agencyA), CrmApi.company("Archivee Test"));
        customers.archive(agency(agencyA), archived).andExpect(status().isOk());

        customers.expectProblem(api.create(agency(agencyA), request(archived, "PHONE")), 409, "customer-archived");
    }

    @Test
    void aRequestCannotBeMovedToAnArchivedCustomer() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID archived = customers.createOk(agency(agencyA), CrmApi.company("Archivee Test"));
        customers.archive(agency(agencyA), archived).andExpect(status().isOk());
        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));

        customers.expectProblem(api.update(agency(agencyA), id, 0, body(archived, id)), 409, "customer-archived");
    }

    @Test
    void archivingTheCustomerKeepsItsRequestsWhichStayEditable() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID id = api.createOk(agency(agencyA), with(request(atlas, "PHONE"), "destination", "Istanbul"));
        customers.archive(agency(agencyA), atlas).andExpect(status().isOk());

        api.get(agency(agencyA), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.id").value(atlas.toString()));
        api.list(agency(agencyA), "customerId=" + atlas).andExpect(jsonPath("$.items[*].id").value(contains(
                id.toString())));
        api.update(agency(agencyA), id, 0, with(body(atlas, id), "destination", "Antalya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Antalya"));
        api.moveTo(agency(agencyA), id, "QUOTING");
    }

    /** Corps de modification : meme responsable, ce client. */
    private Map<String, Object> body(UUID customerId, UUID requestId) throws Exception {
        String owner = api.read(agency(agencyA), requestId, "$.ownerUserId");
        return with(request(customerId, "PHONE"), "ownerUserId", owner);
    }
}
