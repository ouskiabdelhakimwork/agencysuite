package ma.agencysuite.iam.infrastructure.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import ma.agencysuite.iam.application.EmailAlreadyRegisteredException;
import ma.agencysuite.iam.application.IdentityAdminUnavailableException;
import ma.agencysuite.iam.application.ProvisionedOrganization;
import ma.agencysuite.shared.tenant.AgencyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Forme exacte des appels a l'API d'administration Keycloak (verifies sur Keycloak 26.8.2, etape 3
 * lot 5), cache du jeton du compte de service et traduction des erreurs. Aucun Keycloak reel.
 */
class KeycloakIdentityProviderAdminTest {

    private static final String BASE = "https://keycloak.test";
    private static final String ADMIN = BASE + "/admin/realms/agencysuite";
    private static final String TOKEN_URL = BASE + "/realms/agencysuite/protocol/openid-connect/token";
    private static final String SECRET = "secret-de-test-a-ne-jamais-afficher";
    private static final String ACCESS_TOKEN = "jeton-de-test-a-ne-jamais-afficher";
    private static final String EMAIL = "nouvel.utilisateur@example.test";
    private static final String SUB = "0b0f6a52-1f1d-4c39-9a0e-3f9f0b7c2d11";
    private static final String ORG_ID = "4d7b5c1e-8a2f-4e6b-b1c3-9d8e7f6a5b4c";
    private static final UUID AGENCY = UUID.fromString("0190a1b2-0000-7000-8000-0000000000aa");

    private MutableClock clock;
    private MockRestServiceServer server;
    private KeycloakIdentityProviderAdmin admin;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-12-01T10:00:00Z"));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        admin = new KeycloakIdentityProviderAdmin(properties(SECRET), builder, clock);
    }

    @Test
    void createsTheUserWithTheServiceAccountTokenAndReturnsTheIdOfTheLocationHeader() {
        expectToken();
        server.expect(once(), requestTo(ADMIN + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"username": "%s", "email": "%s", "firstName": "Nouvel", "lastName": "Utilisateur",
                         "enabled": true, "emailVerified": false, "attributes": {"locale": ["ar"]}}
                        """.formatted(EMAIL, EMAIL), true))
                .andRespond(withStatus(HttpStatus.CREATED).location(URI.create(ADMIN + "/users/" + SUB)));

        assertThat(admin.createUser(EMAIL, "Nouvel", "Utilisateur", "ar")).isEqualTo(SUB);
        server.verify();
    }

    @Test
    void theServiceAccountTokenIsCachedUntil30SecondsBeforeItsExpiry() {
        expectToken(); // expires_in = 300 s
        expectLogout();
        expectLogout();
        expectToken();
        expectLogout();

        admin.logout(SUB);
        clock.advance(Duration.ofSeconds(269));
        admin.logout(SUB);
        clock.advance(Duration.ofSeconds(2)); // 271 s > 300 - 30
        admin.logout(SUB);

        server.verify();
    }

    @Test
    void anExistingEmailIsEmailAlreadyRegistered() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"User exists with same email\"}"));

        assertThatThrownBy(() -> admin.createUser(EMAIL, "Nouvel", "Utilisateur", "fr"))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    void aServerErrorIsUnavailableWithoutSecretTokenEmailNorKeycloakAnswerInTheMessage() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"boom for " + EMAIL + "\"}"));

        assertThatThrownBy(() -> admin.createUser(EMAIL, "Nouvel", "Utilisateur", "fr"))
                .isInstanceOf(IdentityAdminUnavailableException.class)
                .hasMessageContaining("HTTP 500")
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain(SECRET, ACCESS_TOKEN, EMAIL, "boom"));
    }

    @Test
    void aTimeoutIsUnavailable() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> admin.createUser(EMAIL, "Nouvel", "Utilisateur", "fr"))
                .isInstanceOf(IdentityAdminUnavailableException.class)
                .hasNoCause()
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET, ACCESS_TOKEN, EMAIL));
    }

    @Test
    void aRejectedTokenRequestIsUnavailableWithoutTheSecret() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"unauthorized_client\"}"));

        assertThatThrownBy(() -> admin.logout(SUB))
                .isInstanceOf(IdentityAdminUnavailableException.class)
                .hasMessageContaining("token")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
    }

    @Test
    void a401OnAnAdminCallDropsTheCachedToken() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/" + SUB + "/logout")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken();
        expectLogout();

        assertThatThrownBy(() -> admin.logout(SUB)).isInstanceOf(IdentityAdminUnavailableException.class);
        admin.logout(SUB);

        server.verify();
    }

    @Test
    void withoutSecretNothingIsCalledAndTheAdministrationIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        KeycloakIdentityProviderAdmin unconfigured =
                new KeycloakIdentityProviderAdmin(properties(""), builder, clock);

        assertThatThrownBy(() -> unconfigured.createUser(EMAIL, "Nouvel", "Utilisateur", "fr"))
                .isInstanceOf(IdentityAdminUnavailableException.class);
        silent.verify();
    }

    @Test
    void ensureOrganizationCreatesItWithTheAgencyUuidAsAliasWhenTheExactAliasIsNotFound() {
        String alias = AGENCY.toString();
        expectToken();
        server.expect(requestToUriTemplate(ADMIN + "/organizations?q={q}&briefRepresentation=true", "alias:" + alias))
                .andExpect(method(HttpMethod.GET))
                // Un alias voisin ne compte pas : correspondance exacte verifiee aussi cote client.
                .andRespond(withSuccess("[{\"id\":\"other\",\"alias\":\"" + alias + "-x\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/organizations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"name": "Agence Test (agence-test)", "alias": "%s", "enabled": true}
                        """.formatted(alias), true))
                .andRespond(withStatus(HttpStatus.CREATED).location(URI.create(ADMIN + "/organizations/" + ORG_ID)));

        ProvisionedOrganization organization =
                admin.ensureOrganization(new AgencyId(AGENCY), "Agence Test (agence-test)");

        assertThat(organization).isEqualTo(new ProvisionedOrganization(ORG_ID, true));
        server.verify();
    }

    @Test
    void ensureOrganizationReusesAnExistingOrganization() {
        String alias = AGENCY.toString();
        expectToken();
        server.expect(requestToUriTemplate(ADMIN + "/organizations?q={q}&briefRepresentation=true", "alias:" + alias))
                .andRespond(withSuccess("[{\"id\":\"" + ORG_ID + "\",\"alias\":\"" + alias + "\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(admin.ensureOrganization(new AgencyId(AGENCY), "Agence Test (agence-test)"))
                .isEqualTo(new ProvisionedOrganization(ORG_ID, false));
        server.verify();
    }

    @Test
    void addMemberSendsTheUserIdAsAJsonStringAndIgnoresAnExistingMembership() {
        expectToken();
        server.expect(requestTo(ADMIN + "/organizations/" + ORG_ID + "/members"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("\"" + SUB + "\""))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(ADMIN + "/organizations/" + ORG_ID + "/members"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        admin.addMember(ORG_ID, SUB);
        admin.addMember(ORG_ID, SUB);

        server.verify();
    }

    @Test
    void sendInvitationAsksToVerifyTheEmailAndChooseAPasswordWithALimitedLifespan() {
        expectToken();
        server.expect(requestToUriTemplate(ADMIN + "/users/{id}/execute-actions-email"
                        + "?client_id={client}&redirect_uri={redirect}&lifespan={lifespan}",
                        SUB, "agencysuite-frontend", "http://localhost:4200/", "259200"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(queryParam("lifespan", "259200"))
                .andExpect(content().json("[\"VERIFY_EMAIL\", \"UPDATE_PASSWORD\"]", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        admin.sendInvitation(SUB);

        server.verify();
    }

    @Test
    void setEnabledSendsOnlyTheEnabledFlag() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/" + SUB))
                .andExpect(method(HttpMethod.PUT))
                // Jamais "attributes" : sinon Keycloak efface l'e-mail, le prenom et le nom.
                .andExpect(content().json("{\"enabled\": false}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        admin.setEnabled(SUB, false);

        server.verify();
    }

    @Test
    void deleteUserIgnoresAnAlreadyDeletedUser() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/" + SUB))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        admin.deleteUser(SUB);

        server.verify();
    }

    @Test
    void propertiesNeverPrintTheSecret() {
        assertThat(properties(SECRET).toString()).doesNotContain(SECRET).contains("<defini>");
        assertThat(properties("").toString()).contains("<absent>");
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "agencysuite-backend",
                        "client_secret", SECRET)))
                .andRespond(withSuccess("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":300,"
                        + "\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
    }

    private void expectLogout() {
        server.expect(requestTo(ADMIN + "/users/" + SUB + "/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    private static KeycloakAdminProperties properties(String secret) {
        return new KeycloakAdminProperties(BASE, "agencysuite", "agencysuite-backend", secret,
                Duration.ofHours(72), "http://localhost:4200");
    }

    /** Horloge reglable : expiration du jeton sans attendre. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
