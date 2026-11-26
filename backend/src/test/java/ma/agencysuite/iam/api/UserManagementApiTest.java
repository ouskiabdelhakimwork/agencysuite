package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.iam.FakeIdentityProviderAdmin;
import ma.agencysuite.iam.FakeIdentityProviderAdmin.Operation;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.application.LastManagerException;
import ma.agencysuite.iam.application.UserAdministration;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.UserContext;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** API des utilisateurs de l'agence (/api/v1/users), permission user.manage, isolation entre agences. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserManagementApiTest {

    private static final String USERS = "/api/v1/users";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;
    @Autowired
    private FakeIdentityProviderAdmin identityProvider;
    @Autowired
    private UserAdministration administration;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;
    private UUID managerA;

    @BeforeEach
    void createAgencies() {
        agencyA = data.createAgency("users-a-" + suffix);
        agencyB = data.createAgency("users-b-" + suffix);
        data.subscribe(agencyA, "PRO");
        data.subscribe(agencyB, "PRO");
        managerA = iam.userId(agencyA, TestTokens.managerSub(agencyA));
    }

    @AfterEach
    void cleanUp() {
        identityProvider.reset();
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void invitesAUserAndListsItWithItsStatusAndRoles() throws Exception {
        String body = mvc.perform(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(email("sales"), "[\"SALES\"]")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(USERS + "/")))
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.invitationSent").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get(USERS).with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].email".formatted(id)).value(email("sales")))
                .andExpect(jsonPath("$[?(@.id == '%s')].fullName".formatted(id)).value("Prenom Nom"))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(id)).value("INVITED"))
                .andExpect(jsonPath("$[?(@.id == '%s')].roles[0]".formatted(id)).value("SALES"))
                .andExpect(jsonPath("$[?(@.id == '%s')].roles[0]".formatted(managerA)).value("MANAGER"));
    }

    @Test
    void theLocationHeaderPointsToTheCreatedUser() throws Exception {
        var response = mvc.perform(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(email("loc"), "[\"SALES\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(USERS + "/" + id);
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        expectValidationFailed(inviteBody("pas-un-email", "[\"SALES\"]"));
        expectValidationFailed(inviteBody(email("noroles"), "[]"));
        expectValidationFailed("""
                {"email": "%s", "firstName": "%s", "lastName": "Nom", "roles": ["SALES"]}
                """.formatted(email("long"), "P".repeat(81)));
        expectValidationFailed("""
                {"email": "%s", "firstName": "", "lastName": "Nom", "roles": ["SALES"]}
                """.formatted(email("blank")));
        mvc.perform(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(email("pirate"), "[\"PIRATE\"]")))
                .andExpect(status().isBadRequest());
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void businessErrorsAreTranslatedProblems() throws Exception {
        invite(agencyA, email("dup"), "SALES");

        expectProblem(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                .content(inviteBody(email("dup"), "[\"SALES\"]")), 409, "email-already-registered",
                "E-mail déjà enregistré");

        identityProvider.failOn(Operation.ENSURE_ORGANIZATION);
        String unavailable = expectProblem(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                .content(inviteBody(email("down"), "[\"SALES\"]")), 503, "identity-admin-unavailable",
                "Service d’identité indisponible");
        assertThat(unavailable).doesNotContain("Panne simulee", "ENSURE_ORGANIZATION");

        mvc.perform(post(USERS).with(agency(agencyA)).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content(inviteBody(email("down2"), "[\"SALES\"]")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Identity service unavailable"));
    }

    @Test
    void theQuotaOfThePlanIsApplied() throws Exception {
        UUID starter = data.createAgency("users-s-" + suffix);
        data.subscribe(starter, "STARTER");
        invite(starter, email("s2"), "SALES");
        invite(starter, email("s3"), "SALES");

        expectProblem(post(USERS).with(agency(starter)).contentType(MediaType.APPLICATION_JSON)
                .content(inviteBody(email("s4"), "[\"SALES\"]")), 409, "user-quota-exceeded",
                "Nombre d’utilisateurs atteint");
    }

    @Test
    void aUserWithoutUserManageIsRefused() throws Exception {
        String sales = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");

        mvc.perform(get(USERS).with(agencyUser(agencyA, sales)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:permission-denied"))
                .andExpect(jsonPath("$.permission").value(PermissionCode.USER_MANAGE.code()));
        mvc.perform(post(USERS).with(agencyUser(agencyA, sales)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(email("bysales"), "[\"SALES\"]")))
                .andExpect(status().isForbidden());
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void aUserOfAnotherAgencyIs404AndNeverListed() throws Exception {
        UUID managerB = iam.userId(agencyB, TestTokens.managerSub(agencyB));
        String inB = invite(agencyB, email("in-b"), "SALES");

        for (MockHttpServletRequestBuilder request : List.of(
                put(USERS + "/" + managerB + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\": [\"SALES\"]}"),
                post(USERS + "/" + inB + "/invitation"),
                post(USERS + "/" + managerB + "/disable"),
                post(USERS + "/" + managerB + "/enable"))) {
            expectProblem(request.with(agency(agencyA)), 404, "not-found", "Ressource introuvable");
        }
        mvc.perform(get(USERS).with(agency(agencyA)))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(managerB.toString()))))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inB))));
        assertThat(iam.status(agencyB, managerB)).isEqualTo(UserStatus.ACTIVE);
        assertThat(iam.roles(agencyB, managerB)).containsExactly("MANAGER");
        assertThat(identityProvider.calls(Operation.SEND_INVITATION)).hasSize(1); // celle de l'invitation dans B
    }

    @Test
    void rolesAreReplacedAndEachChangeIsAudited() throws Exception {
        String user = invite(agencyA, email("roles"), "SALES");

        mvc.perform(put(USERS + "/" + user + "/roles").with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\": [\"OPERATIONS\", \"ACCOUNTANT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.roles[1]").value("OPERATIONS"));

        assertThat(iam.roles(agencyA, UUID.fromString(user))).containsExactly("ACCOUNTANT", "OPERATIONS");
        List<Row> changes = audit.entries(agencyA).stream()
                .filter(row -> row.action().startsWith("ROLE_")).toList();
        assertThat(changes).extracting(Row::action, Row::details).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("ROLE_REVOKED", "{\"role\": \"SALES\"}"),
                org.assertj.core.groups.Tuple.tuple("ROLE_ASSIGNED", "{\"role\": \"ACCOUNTANT\"}"),
                org.assertj.core.groups.Tuple.tuple("ROLE_ASSIGNED", "{\"role\": \"OPERATIONS\"}"));
        assertThat(changes).allSatisfy(row -> {
            assertThat(row.entityId()).isEqualTo(user);
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(managerA);
        });
    }

    @Test
    void theLastManagerKeepsItsRoleUntilAnotherManagerExists() throws Exception {
        expectProblem(put(USERS + "/" + managerA + "/roles").with(agency(agencyA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\": [\"SALES\"]}"),
                409, "last-manager", "Dernier gérant");
        assertThat(iam.roles(agencyA, managerA)).containsExactly("MANAGER");

        invite(agencyA, email("manager2"), "MANAGER"); // un invite compte comme gerant
        mvc.perform(put(USERS + "/" + managerA + "/roles").with(agency(agencyA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\": [\"SALES\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void aDisabledManagerDoesNotCountAsTheRemainingManager() {
        String other = iam.createUser(agencyA, UserStatus.ACTIVE, "MANAGER");
        iam.disableUser(agencyA, other);
        // Appel direct : par l'API, seul un gerant (user.manage) peut agir, et il ne peut pas se desactiver.
        CurrentUser operator = new CurrentUser(UUID.randomUUID(), Set.of(PermissionCode.USER_MANAGE));

        assertThatThrownBy(() -> TenantContext.runAsAgency(new AgencyId(agencyA), () -> UserContext.runAs(operator,
                () -> administration.disable(managerA))))
                .isInstanceOf(LastManagerException.class);
        assertThat(iam.status(agencyA, managerA)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void aManagerCannotDisableItself() throws Exception {
        expectProblem(post(USERS + "/" + managerA + "/disable").with(agency(agencyA)), 409, "cannot-disable-self",
                "Action impossible sur votre compte");
        assertThat(iam.status(agencyA, managerA)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void aDisabledUserIsRefusedAtItsNextRequestAndDisabledInKeycloak() throws Exception {
        String sub = iam.createUser(agencyA, UserStatus.ACTIVE, "MANAGER");
        UUID user = iam.userId(agencyA, sub);
        mvc.perform(get(USERS).with(agencyUser(agencyA, sub))).andExpect(status().isOk());

        mvc.perform(post(USERS + "/" + user + "/disable").with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityProviderSynced").value(true));

        mvc.perform(get(USERS).with(agencyUser(agencyA, sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        assertThat(iam.status(agencyA, user)).isEqualTo(UserStatus.DISABLED);
        assertThat(identityProvider.calls()).extracting(FakeIdentityProviderAdmin.Call::operation, FakeIdentityProviderAdmin.Call::target)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Operation.SET_ENABLED, sub),
                        org.assertj.core.groups.Tuple.tuple(Operation.LOGOUT, sub));
        assertThat(audit.entries(agencyA)).extracting(Row::action).containsExactly("USER_DISABLED");
    }

    @Test
    void aKeycloakFailureWhileDisablingStillBlocksTheUser() throws Exception {
        String sub = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");
        UUID user = iam.userId(agencyA, sub);
        identityProvider.failOn(Operation.SET_ENABLED);

        mvc.perform(post(USERS + "/" + user + "/disable").with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityProviderSynced").value(false));

        assertThat(iam.status(agencyA, user)).isEqualTo(UserStatus.DISABLED);
        mvc.perform(get("/api/v1/me").with(agencyUser(agencyA, sub))).andExpect(status().isForbidden());
    }

    @Test
    void enablingRestoresInvitedWhenTheUserNeverLoggedInAndAppliesTheQuota() throws Exception {
        String user = invite(agencyA, email("back"), "SALES");
        mvc.perform(post(USERS + "/" + user + "/disable").with(agency(agencyA))).andExpect(status().isOk());

        mvc.perform(post(USERS + "/" + user + "/enable").with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityProviderSynced").value(true));
        assertThat(iam.status(agencyA, UUID.fromString(user))).isEqualTo(UserStatus.INVITED);

        UUID starter = data.createAgency("users-q-" + suffix);
        data.subscribe(starter, "STARTER");
        String s2 = invite(starter, email("q2"), "SALES");
        mvc.perform(post(USERS + "/" + s2 + "/disable").with(agency(starter))).andExpect(status().isOk());
        invite(starter, email("q3"), "SALES");
        invite(starter, email("q4"), "SALES");
        expectProblem(post(USERS + "/" + s2 + "/enable").with(agency(starter)), 409, "user-quota-exceeded",
                "Nombre d’utilisateurs atteint");
        assertThat(audit.entries(agencyA)).extracting(Row::action).contains("USER_DISABLED", "USER_ENABLED");
    }

    @Test
    void theInvitationIsResentOnlyToAnInvitedUser() throws Exception {
        String user = invite(agencyA, email("resend"), "SALES");
        identityProvider.reset();

        mvc.perform(post(USERS + "/" + user + "/invitation").with(agency(agencyA))).andExpect(status().isAccepted());
        assertThat(identityProvider.calls(Operation.SEND_INVITATION)).hasSize(1);
        assertThat(audit.entries(agencyA)).extracting(Row::action).contains("INVITATION_RESENT");

        expectProblem(post(USERS + "/" + managerA + "/invitation").with(agency(agencyA)), 409, "user-not-invited",
                "Utilisateur non invité");
    }

    @Test
    void aResendFailureIs503AndNotAudited() throws Exception {
        String user = invite(agencyA, email("resend-ko"), "SALES");
        identityProvider.failOn(Operation.SEND_INVITATION);

        expectProblem(post(USERS + "/" + user + "/invitation").with(agency(agencyA)), 503,
                "identity-admin-unavailable", "Service d’identité indisponible");
        assertThat(audit.entries(agencyA)).extracting(Row::action).doesNotContain("INVITATION_RESENT");
    }

    /** Invite par l'API (gerant de l'agence) ; renvoie l'identifiant du nouvel utilisateur. */
    private String invite(UUID agencyId, String email, String role) throws Exception {
        String body = mvc.perform(post(USERS).with(agency(agencyId)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody(email, "[\"" + role + "\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void expectValidationFailed(String body) throws Exception {
        mvc.perform(post(USERS).with(agency(agencyA)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:validation-failed"));
    }

    private String expectProblem(MockHttpServletRequestBuilder request, int status, String code, String title)
            throws Exception {
        ResultActions result = mvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:" + code))
                .andExpect(jsonPath("$.title").value(title));
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String inviteBody(String email, String roles) {
        return """
                {"email": "%s", "firstName": "Prenom", "lastName": "Nom", "roles": %s}
                """.formatted(email, roles);
    }

    private String email(String name) {
        return name + "-" + suffix + "@example.test";
    }
}
