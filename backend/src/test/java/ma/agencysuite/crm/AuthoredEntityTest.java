package ma.agencysuite.crm;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.CrmApi.company;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.domain.Contact;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.infrastructure.ContactRepository;
import ma.agencysuite.crm.infrastructure.CustomerRepository;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.tenant.AgencyId;
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
 * Auteur des lignes ({@code AuthoredAgencyEntity}) : created_by et updated_by valent l'identifiant
 * de l'utilisateur courant ; seul updated_by change a la modification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthoredEntityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private ContactRepository contacts;
    @Autowired
    private TransactionTemplate transaction;

    private final String suffix = PlatformTestData.randomSuffix();
    private CrmApi api;
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        api = new CrmApi(mvc);
        agencyId = data.createAgency("authored-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createdByAndUpdatedByAreTheCurrentUser() throws Exception {
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));
        String salesSub = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");
        UUID sales = iam.userId(agencyId, salesSub);

        UUID id = api.createOk(agency(agencyId), company("Societe Test"));
        Customer created = customer(id);
        assertThat(created.getCreatedBy()).isEqualTo(manager);
        assertThat(created.getUpdatedBy()).isEqualTo(manager);

        api.update(agencyUser(agencyId, salesSub), id, 0, company("Societe Modifiee Test"))
                .andExpect(status().isOk());
        Customer updated = customer(id);
        assertThat(updated.getCreatedBy()).isEqualTo(manager);
        assertThat(updated.getUpdatedBy()).isEqualTo(sales);
    }

    @Test
    void contactsKeepTheirAuthorToo() throws Exception {
        UUID manager = iam.userId(agencyId, TestTokens.managerSub(agencyId));
        String salesSub = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");
        UUID sales = iam.userId(agencyId, salesSub);
        UUID customer = api.createOk(agency(agencyId), company("Societe Test"));

        UUID contactId = api.addContactOk(agencyUser(agencyId, salesSub), customer, "Amine Test", false);

        Contact contact = asAgency(() -> contacts.findById(contactId).orElseThrow());
        assertThat(contact.getCreatedBy()).isEqualTo(sales);
        assertThat(contact.getUpdatedBy()).isEqualTo(sales);
        assertThat(customer(customer).getCreatedBy()).isEqualTo(manager);
    }

    @Test
    void withoutAnActorTheAuthorIsTheSystem() {
        Customer saved = asAgency(() -> customers.saveAndFlush(Customer.create(new ma.agencysuite.crm.domain.CustomerData(
                ma.agencysuite.crm.domain.CustomerType.COMPANY, "Societe Systeme Test", null, null, null, null, null,
                null, null, null, null, null, null, null, null))));

        assertThat(customer(saved.getId()).getCreatedBy()).isNull();
        assertThat(customer(saved.getId()).getUpdatedBy()).isNull();
    }

    private Customer customer(UUID id) {
        return asAgency(() -> customers.findById(id).orElseThrow());
    }

    private <T> T asAgency(java.util.function.Supplier<T> work) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> transaction.execute(status -> work.get()));
    }
}
