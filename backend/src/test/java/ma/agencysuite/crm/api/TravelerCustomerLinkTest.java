package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.crm.TravelerApi.traveler;
import static ma.agencysuite.crm.TravelerApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.TravelerApi;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.tenant.TenantContext;
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
 * Rattachement d'un voyageur a zero, un ou plusieurs clients de l'agence : idempotence, clients
 * archives, inconnus ou d'une autre agence, detachement, rattachement a la creation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TravelerCustomerLinkTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    private final String suffix = PlatformTestData.randomSuffix();
    private TravelerApi api;
    private CrmApi customers;
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createAgencies() {
        api = new TravelerApi(mvc);
        customers = new CrmApi(mvc);
        agencyA = data.createAgency("link-a-" + suffix);
        agencyB = data.createAgency("link-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void aTravelerCanBeLinkedToSeveralCustomers() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID bis = customers.createOk(agency(agencyA), CrmApi.company("Atlas Bis Test"));
        UUID id = api.createOk(agency(agencyA), traveler("El Fassi", "Zineb"));

        api.link(agency(agencyA), id, atlas).andExpect(status().isOk())
                .andExpect(jsonPath("$.customers[*].name").value(contains("Atlas Pharma Test")));
        api.link(agency(agencyA), id, bis)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers[*].name").value(contains("Atlas Bis Test", "Atlas Pharma Test")))
                .andExpect(jsonPath("$.customers[0].id").value(bis.toString()))
                .andExpect(jsonPath("$.customers[0].type").value("COMPANY"))
                .andExpect(jsonPath("$.customers[0].archived").value(false));
        api.get(agency(agencyA), id).andExpect(jsonPath("$.customers.length()").value(2));
        api.list(agency(agencyA), "customerId=" + atlas).andExpect(jsonPath("$.items[*].id").value(
                contains(id.toString())));
        api.list(agency(agencyA), "customerId=" + bis).andExpect(jsonPath("$.totalElements").value(1));
        // La fiche client du lot 1 ne change pas.
        customers.get(agency(agencyA), atlas).andExpect(jsonPath("$.travelers").doesNotExist());
    }

    @Test
    void linkingTwiceIsIdempotentAndRecordsTheAuthor() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID id = api.createOk(agency(agencyA), traveler("El Fassi", "Zineb"));

        api.link(agency(agencyA), id, atlas).andExpect(status().isOk());
        api.link(agency(agencyA), id, atlas).andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(1));

        Object[] row = linkRow(id);
        assertThat(row[0]).isEqualTo(agencyA);
        assertThat(row[1]).isEqualTo(iam.userId(agencyA, TestTokens.managerSub(agencyA)));
        assertThat(row[2]).isNotNull();
        assertThat(linkCount(id)).isEqualTo(1);
    }

    @Test
    void anArchivedCustomerCannotBeLinked() throws Exception {
        UUID archived = customers.createOk(agency(agencyA), CrmApi.company("Archivee Test"));
        customers.archive(agency(agencyA), archived).andExpect(status().isOk());
        UUID id = api.createOk(agency(agencyA), traveler("El Fassi", "Zineb"));

        String body = api.expectProblem(api.link(agency(agencyA), id, archived), 409, "customer-archived");
        assertThat(body).contains("\"title\"").doesNotContain("Exception");
        api.expectProblem(api.create(agency(agencyA), with(traveler("Autre", "Test"), "customerIds",
                List.of(archived))), 409, "customer-archived");
        api.get(agency(agencyA), id).andExpect(jsonPath("$.customers").isEmpty());
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customerArchivedProblemIsTranslated() throws Exception {
        UUID archived = customers.createOk(agency(agencyA), CrmApi.company("Archivee Test"));
        customers.archive(agency(agencyA), archived).andExpect(status().isOk());
        UUID id = api.createOk(agency(agencyA), traveler("El Fassi", "Zineb"));

        for (String[] expected : List.of(new String[] {"fr", "Client archivé"}, new String[] {"en", "Customer archived"},
                new String[] {"ar", "عميل مؤرشف"})) {
            customers.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put(TravelerApi.TRAVELERS + "/" + id + "/customers/" + archived)
                            .header("Accept-Language", expected[0]).with(agency(agencyA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value(expected[1]));
        }
    }

    @Test
    void anUnknownCustomerOrACustomerOfAnotherAgencyIs404() throws Exception {
        UUID customerOfB = customers.createOk(agency(agencyB), CrmApi.company("Societe B Test"));
        UUID id = api.createOk(agency(agencyA), traveler("El Fassi", "Zineb"));

        api.expectProblem(api.link(agency(agencyA), id, customerOfB), 404, "not-found");
        api.expectProblem(api.link(agency(agencyA), id, UUID.randomUUID()), 404, "not-found");
        api.expectProblem(api.create(agency(agencyA), with(traveler("Autre", "Test"), "customerIds",
                List.of(customerOfB))), 404, "not-found");
        api.expectProblem(api.link(agency(agencyA), UUID.randomUUID(), customerOfB), 404, "not-found");
        api.get(agency(agencyA), id).andExpect(jsonPath("$.customers").isEmpty());
        assertThat(linkCount(id)).isZero();
    }

    @Test
    void unlinkingRemovesTheLinkAndIsIdempotent() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID bis = customers.createOk(agency(agencyA), CrmApi.company("Atlas Bis Test"));
        UUID id = api.createOk(agency(agencyA), with(traveler("El Fassi", "Zineb"), "customerIds", List.of(atlas, bis)));

        api.unlink(agency(agencyA), id, atlas).andExpect(status().isNoContent());
        api.unlink(agency(agencyA), id, atlas).andExpect(status().isNoContent());
        api.unlink(agency(agencyA), id, UUID.randomUUID()).andExpect(status().isNoContent());

        api.get(agency(agencyA), id).andExpect(jsonPath("$.customers[*].name").value(contains("Atlas Bis Test")));
        api.list(agency(agencyA), "customerId=" + atlas).andExpect(jsonPath("$.totalElements").value(0));
        api.expectProblem(api.unlink(agency(agencyA), UUID.randomUUID(), bis), 404, "not-found");
        assertThat(linkCount(id)).isEqualTo(1);
    }

    @Test
    void customerIdsAtCreationLinkTheTravelerOnce() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID karim = customers.createOk(agency(agencyA), CrmApi.individual("Karim Test"));
        // Un client particulier n'est pas automatiquement son propre voyageur.
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(0));

        api.create(agency(agencyA), with(traveler("El Fassi", "Zineb"), "customerIds", List.of(atlas, karim, atlas)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customers[*].name").value(contains("Atlas Pharma Test", "Karim Test")))
                .andExpect(jsonPath("$.customers[1].type").value("INDIVIDUAL"));
        api.expectProblem(api.create(agency(agencyA), with(traveler("Autre", "Test"), "customerIds",
                java.util.Arrays.asList(atlas, null))), 400, "validation-failed");
        api.list(agency(agencyA), "customerId=" + karim).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void archivingACustomerKeepsTheLink() throws Exception {
        UUID atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
        UUID id = api.createOk(agency(agencyA), with(traveler("El Fassi", "Zineb"), "customerIds", List.of(atlas)));

        customers.archive(agency(agencyA), atlas).andExpect(status().isOk());

        api.get(agency(agencyA), id)
                .andExpect(jsonPath("$.customers[*].id").value(contains(atlas.toString())))
                .andExpect(jsonPath("$.customers[0].archived").value(true));
        api.list(agency(agencyA), "customerId=" + atlas).andExpect(jsonPath("$.totalElements").value(1));
        // Le lien existant ne se recree pas : 409, et il reste en place.
        api.expectProblem(api.link(agency(agencyA), id, atlas), 409, "customer-archived");
        api.unlink(agency(agencyA), id, atlas).andExpect(status().isNoContent());
        assertThat(linkCount(id)).isZero();
    }

    private Object[] linkRow(UUID travelerId) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> (Object[]) entityManager
                .createNativeQuery("SELECT agency_id, created_by, created_at FROM customer_traveler "
                        + "WHERE traveler_id = :id")
                .setParameter("id", travelerId).getSingleResult()));
    }

    private long linkCount(UUID travelerId) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> ((Number) entityManager
                .createNativeQuery("SELECT count(*) FROM customer_traveler WHERE traveler_id = :id")
                .setParameter("id", travelerId).getSingleResult()).longValue()));
    }
}
