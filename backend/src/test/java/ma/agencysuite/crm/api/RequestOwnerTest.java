package ma.agencysuite.crm.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.crm.RequestApi.request;
import static ma.agencysuite.crm.RequestApi.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.crm.CrmApi;
import ma.agencysuite.crm.RequestApi;
import ma.agencysuite.crm.application.CustomerRequestRegistry;
import ma.agencysuite.crm.application.CustomerRequestRegistry.RequestView;
import ma.agencysuite.crm.application.OwnerRequiredException;
import ma.agencysuite.crm.domain.CustomerRequestData;
import ma.agencysuite.crm.domain.RequestChannel;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.security.Actor;
import ma.agencysuite.shared.security.ActorContext;
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

/**
 * Responsable d'une demande : l'utilisateur courant par defaut, sinon un utilisateur de la meme
 * agence ; un membre de l'editeur agissant dans une agence doit le donner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestOwnerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private CustomerRequestRegistry registry;

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
        agencyA = data.createAgency("reqo-a-" + suffix);
        agencyB = data.createAgency("reqo-b-" + suffix);
        atlas = customers.createOk(agency(agencyA), CrmApi.company("Atlas Pharma Test"));
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void theOwnerIsTheCurrentUserByDefault() throws Exception {
        String salesSub = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");
        UUID sales = iam.userId(agencyA, salesSub);

        api.create(agencyUser(agencyA, salesSub), request(atlas, "PHONE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(sales.toString()));
    }

    @Test
    void anotherUserOfTheAgencyCanBeTheOwner() throws Exception {
        UUID invited = iam.userId(agencyA, iam.createUser(agencyA, UserStatus.INVITED, "SALES"));

        api.create(agency(agencyA), with(request(atlas, "PHONE"), "ownerUserId", invited))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(invited.toString()));
    }

    @Test
    void anOwnerOfAnotherAgencyOrAnUnknownOwnerIsRefused() throws Exception {
        UUID managerOfB = iam.userId(agencyB, TestTokens.managerSub(agencyB));

        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "ownerUserId", managerOfB)),
                400, "invalid-owner");
        customers.expectProblem(api.create(agency(agencyA), with(request(atlas, "PHONE"), "ownerUserId",
                UUID.randomUUID())), 400, "invalid-owner");
        api.list(agency(agencyA), "").andExpect(jsonPath("$.totalElements").value(0));

        UUID id = api.createOk(agency(agencyA), request(atlas, "PHONE"));
        customers.expectProblem(api.update(agency(agencyA), id, 0, with(request(atlas, "PHONE"), "ownerUserId",
                managerOfB)), 400, "invalid-owner");
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));
        api.get(agency(agencyA), id).andExpect(jsonPath("$.ownerUserId").value(manager.toString()))
                .andExpect(jsonPath("$.version").value(0));
    }

    /**
     * Un membre de l'editeur n'a pas de compte user_account : sans responsable, refus (400
     * validation-failed par CrmProblemHandler) ; avec un responsable de l'agence, la demande est
     * creee. Aucune route HTTP d'agence n'accepte aujourd'hui son jeton : le cas d'usage est appele
     * directement, dans le perimetre de l'agence.
     */
    @Test
    void aPlatformUserActingInAnAgencyMustGiveTheOwner() {
        Actor staff = Actor.platformUser(UUID.randomUUID());
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));

        assertThatThrownBy(() -> asStaff(staff, () -> registry.create(data(null))))
                .isInstanceOf(OwnerRequiredException.class);

        RequestView created = asStaff(staff, () -> registry.create(data(manager)));
        assertThat(created.request().getOwnerUserId()).isEqualTo(manager);
        assertThat(created.request().getCreatedBy()).isEqualTo(staff.id());
    }

    private CustomerRequestData data(UUID owner) {
        return new CustomerRequestData(atlas, owner, RequestChannel.PHONE, null, null, null, null, null, null, null,
                null, null);
    }

    private <T> T asStaff(Actor staff, java.util.function.Supplier<T> work) {
        return TenantContext.callAsAgency(new AgencyId(agencyA), () -> ActorContext.callAs(staff, work));
    }
}
