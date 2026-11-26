package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.iam.FakeIdentityProviderAdmin;
import ma.agencysuite.iam.FakeIdentityProviderAdmin.Operation;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** POST /api/v1/backoffice/agencies/{id}/manager : Organization de l'agence et invitation du gerant. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BackofficeManagerApiTest {

    /** platform_user.id du membre ADMIN insere par V1001 (TestTokens.PLATFORM_ADMIN_SUB). */
    private static final UUID PLATFORM_ADMIN_ID = UUID.fromString("7e57ad41-0000-7000-8000-000000000001");

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

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("bo-mgr-" + suffix);
        data.subscribe(agencyId, "PRO");
    }

    @AfterEach
    void cleanUp() {
        identityProvider.reset();
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void createsTheOrganizationOnceAndInvitesAManagerInTheTargetedAgency() throws Exception {
        String body = mvc.perform(manager(agencyId, email("first")).with(platform()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.invitationSent").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID first = UUID.fromString(JsonPath.read(body, "$.id"));
        String organization = identityProvider.organizationOf(agencyId).orElseThrow();

        String second = JsonPath.read(mvc.perform(manager(agencyId, email("second")).with(platform()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(identityProvider.organizationOf(agencyId)).hasValue(organization);
        // Ecrit dans l'agence ciblee (lu dans son perimetre), avec le role MANAGER.
        assertThat(iam.status(agencyId, first)).isEqualTo(UserStatus.INVITED);
        assertThat(iam.roles(agencyId, first)).containsExactly("MANAGER");
        assertThat(iam.roles(agencyId, UUID.fromString(second))).containsExactly("MANAGER");
        assertThat(identityProvider.membersOf(organization)).hasSize(2);

        List<Row> entries = audit.entries(agencyId);
        assertThat(entries).extracting(Row::action)
                .containsExactly("ORGANIZATION_PROVISIONED", "USER_INVITED", "USER_INVITED");
        assertThat(entries.getFirst().entityType()).isEqualTo("ORGANIZATION");
        assertThat(entries.getFirst().entityId()).isEqualTo(organization);
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.agencyId()).isEqualTo(agencyId);
            assertThat(row.actorType()).isEqualTo("PLATFORM_USER");
            assertThat(row.actorId()).isEqualTo(PLATFORM_ADMIN_ID);
        });
        assertThat(entries.get(1).details()).isEqualTo("{\"roles\": [\"MANAGER\"]}");
    }

    @Test
    void nothingIsWrittenOutsideTheTargetedAgency() throws Exception {
        UUID other = data.createAgency("bo-other-" + suffix);
        long usersBefore = iam.countUsers(other);

        mvc.perform(manager(agencyId, email("only-here")).with(platform())).andExpect(status().isCreated());

        assertThat(iam.countUsers(other)).isEqualTo(usersBefore);
        assertThat(audit.entries(other)).isEmpty();
        assertThat(identityProvider.organizationOf(other)).isEmpty();
    }

    @Test
    void anUnknownAgencyIs404() throws Exception {
        mvc.perform(manager(UUID.randomUUID(), email("nowhere")).with(platform()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void aClosedAgencyIs409() throws Exception {
        data.close(agencyId);

        mvc.perform(manager(agencyId, email("closed")).with(platform()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:agency-closed"))
                .andExpect(jsonPath("$.title").value("Agence close"));
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void anAgencyTokenIsRefused() throws Exception {
        mvc.perform(manager(agencyId, email("byagency")).with(agency(agencyId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        mvc.perform(manager(agencyId, email("anonymous"))).andExpect(status().isUnauthorized());
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void anInvalidRequestIs400() throws Exception {
        mvc.perform(post("/api/v1/backoffice/agencies/" + agencyId + "/manager").with(platform())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"pas-un-email\", \"firstName\": \"Gerant\", \"lastName\": \"Test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:validation-failed"));
    }

    @Test
    void keycloakUnavailableIs503() throws Exception {
        identityProvider.failOn(Operation.ENSURE_ORGANIZATION);

        mvc.perform(manager(agencyId, email("down")).with(platform()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:identity-admin-unavailable"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder manager(UUID agency,
            String email) {
        return post("/api/v1/backoffice/agencies/" + agency + "/manager")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"firstName\": \"Gerant\", \"lastName\": \"Test\"}".formatted(email));
    }

    private String email(String name) {
        return name + "-" + suffix + "@example.test";
    }
}
