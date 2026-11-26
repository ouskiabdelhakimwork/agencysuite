package ma.agencysuite.iam.infrastructure.keycloak;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import ma.agencysuite.iam.application.EmailAlreadyRegisteredException;
import ma.agencysuite.iam.application.IdentityAdminUnavailableException;
import ma.agencysuite.iam.application.IdentityProviderAdmin;
import ma.agencysuite.iam.application.ProvisionedOrganization;
import ma.agencysuite.shared.tenant.AgencyId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link IdentityProviderAdmin} sur l'API d'administration de Keycloak 26.8 (realm agencysuite),
 * avec le {@code RestClient} de Spring : pas de {@code keycloak-admin-client}, lie a la version du
 * serveur, pour les quelques appels utilises. Seuls les appels verifies sur le Keycloak local
 * (etape 3, lot 5, section 2 du plan) sont utilises :
 * <ul>
 *   <li>Organization retrouvee par {@code GET .../organizations?q=alias:<uuid>} (correspondance exacte ;
 *       le parametre {@code search} ne porte que sur le nom), creee par {@code POST .../organizations}
 *       sans domaine ;</li>
 *   <li>utilisateur cree par {@code POST .../users} (identifiant = {@code sub}, dans {@code Location}),
 *       409 si l'e-mail existe ;</li>
 *   <li>membre ajoute par {@code POST .../organizations/{id}/members}, corps = identifiant en chaine JSON ;</li>
 *   <li>invitation par {@code PUT .../users/{id}/execute-actions-email} ;</li>
 *   <li>activation par {@code PUT .../users/{id}} avec <strong>uniquement</strong> {@code enabled} : une
 *       representation contenant {@code attributes} remplace tout le profil et efface l'e-mail, le prenom
 *       et le nom.</li>
 * </ul>
 *
 * <p>Jeton du compte de service (flux client credentials, portee {@code kc-admin-api} par defaut du
 * client), garde en memoire jusqu'a 30 secondes avant son expiration. Le secret, le jeton, les e-mails
 * et les reponses de Keycloak ne sont jamais journalises ni recopies dans les messages d'erreur.
 */
class KeycloakIdentityProviderAdmin implements IdentityProviderAdmin {

    /** Client public de l'application web : retour de l'utilisateur apres l'invitation. */
    static final String INVITATION_CLIENT_ID = "agencysuite-frontend";
    static final List<String> INVITATION_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PASSWORD");
    static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(30);

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() { };

    private final KeycloakAdminProperties properties;
    private final RestClient http;
    private final Clock clock;
    private final Object tokenLock = new Object();
    private volatile CachedToken token;

    KeycloakIdentityProviderAdmin(KeycloakAdminProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.clock = clock;
    }

    private record CachedToken(String value, Instant refreshAfter) {
    }

    @Override
    public ProvisionedOrganization ensureOrganization(AgencyId agencyId, String name) {
        String alias = agencyId.value().toString();
        Optional<String> existing = findOrganization(alias);
        if (existing.isPresent()) {
            return new ProvisionedOrganization(existing.get(), false);
        }
        try {
            ResponseEntity<Void> created = http.post()
                    .uri("/admin/realms/{realm}/organizations", properties.realm())
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", name, "alias", alias, "enabled", true))
                    .retrieve()
                    .toBodilessEntity();
            return new ProvisionedOrganization(createdId(created, "createOrganization"), true);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                // Creation concurrente (meme alias) : on reprend l'Organization existante. Sinon, nom deja pris.
                return findOrganization(alias)
                        .map(id -> new ProvisionedOrganization(id, false))
                        .orElseThrow(() -> unavailable("createOrganization", e));
            }
            throw unavailable("createOrganization", e);
        } catch (RestClientException e) {
            throw unavailable("createOrganization", e);
        }
    }

    @Override
    public String createUser(String email, String firstName, String lastName, String locale) {
        Map<String, Object> user = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("locale", List.of(locale)));
        try {
            ResponseEntity<Void> created = http.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
            return createdId(created, "createUser");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new EmailAlreadyRegisteredException();
            }
            throw unavailable("createUser", e);
        } catch (RestClientException e) {
            throw unavailable("createUser", e);
        }
    }

    @Override
    public void addMember(String organizationId, String sub) {
        requireUuid(organizationId);
        requireUuid(sub);
        try {
            http.post()
                    .uri("/admin/realms/{realm}/organizations/{org}/members", properties.realm(), organizationId)
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("\"" + sub + "\"")
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.CONFLICT.value()) { // 409 : deja membre
                throw unavailable("addMember", e);
            }
        } catch (RestClientException e) {
            throw unavailable("addMember", e);
        }
    }

    @Override
    public void sendInvitation(String sub) {
        requireUuid(sub);
        call("sendInvitation", () -> http.put()
                .uri(builder -> builder.path("/admin/realms/{realm}/users/{id}/execute-actions-email")
                        .queryParam("client_id", INVITATION_CLIENT_ID)
                        .queryParam("redirect_uri", redirectUri())
                        .queryParam("lifespan", properties.invitationLifespan().toSeconds())
                        .build(properties.realm(), sub))
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(INVITATION_ACTIONS)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void setEnabled(String sub, boolean enabled) {
        requireUuid(sub);
        // Uniquement "enabled" : voir la Javadoc de la classe (attributes efface le profil).
        call("setEnabled", () -> http.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), sub)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void logout(String sub) {
        requireUuid(sub);
        call("logout", () -> http.post()
                .uri("/admin/realms/{realm}/users/{id}/logout", properties.realm(), sub)
                .headers(this::authorize)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void deleteUser(String sub) {
        requireUuid(sub);
        try {
            http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), sub)
                    .headers(this::authorize)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) { // 404 : deja supprime
                throw unavailable("deleteUser", e);
            }
        } catch (RestClientException e) {
            throw unavailable("deleteUser", e);
        }
    }

    private Optional<String> findOrganization(String alias) {
        List<Map<String, Object>> found = call("findOrganization", () -> http.get()
                .uri(builder -> builder.path("/admin/realms/{realm}/organizations")
                        .queryParam("q", "alias:" + alias)
                        .queryParam("briefRepresentation", true)
                        .build(properties.realm()))
                .headers(this::authorize)
                .retrieve()
                .body(JSON_ARRAY));
        // Correspondance exacte verifiee en plus du filtre de Keycloak.
        return found == null ? Optional.empty() : found.stream()
                .filter(organization -> alias.equals(organization.get("alias")))
                .map(organization -> String.valueOf(organization.get("id")))
                .findFirst();
    }

    private String redirectUri() {
        String base = properties.appBaseUrl();
        return base.endsWith("/") ? base : base + "/";
    }

    /** En-tete Authorization avec le jeton du compte de service (obtenu ou renouvele si besoin). */
    private void authorize(HttpHeaders headers) {
        headers.setBearerAuth(accessToken());
    }

    private String accessToken() {
        if (!properties.configured()) {
            throw new IdentityAdminUnavailableException("Keycloak admin : secret du compte de service absent");
        }
        CachedToken cached = token;
        if (cached != null && clock.instant().isBefore(cached.refreshAfter())) {
            return cached.value();
        }
        synchronized (tokenLock) {
            cached = token;
            if (cached != null && clock.instant().isBefore(cached.refreshAfter())) {
                return cached.value();
            }
            token = requestToken();
            return token.value();
        }
    }

    private CachedToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        Map<String, Object> response = call("token", () -> http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JSON_OBJECT));
        if (response == null || !(response.get("access_token") instanceof String value) || value.isBlank()
                || !(response.get("expires_in") instanceof Number expiresIn)) {
            throw new IdentityAdminUnavailableException("Keycloak admin token : reponse inattendue");
        }
        Instant refreshAfter = clock.instant().plusSeconds(expiresIn.longValue()).minus(TOKEN_REFRESH_MARGIN);
        return new CachedToken(value, refreshAfter);
    }

    /** Appel sans traitement particulier des statuts : toute erreur rend l'administration indisponible. */
    private <T> T call(String operation, Supplier<T> work) {
        try {
            return work.get();
        } catch (RestClientException e) {
            throw unavailable(operation, e);
        }
    }

    /**
     * Erreur sans cause chainee ni message d'origine : ils peuvent contenir la reponse de Keycloak
     * (e-mail) ou l'adresse appelee. Un 401 invalide le jeton en cache (revoque ou cle changee).
     */
    private IdentityAdminUnavailableException unavailable(String operation, RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                token = null;
            }
            return new IdentityAdminUnavailableException(
                    "Keycloak admin " + operation + " : HTTP " + response.getStatusCode().value());
        }
        return new IdentityAdminUnavailableException("Keycloak admin " + operation + " : "
                + e.getClass().getSimpleName());
    }

    private static String createdId(ResponseEntity<Void> created, String operation) {
        URI location = created.getHeaders().getLocation();
        String path = location == null ? null : location.getPath();
        String id = path == null ? null : path.substring(path.lastIndexOf('/') + 1);
        if (id == null || !CANONICAL_UUID.matcher(id).matches()) {
            throw new IdentityAdminUnavailableException("Keycloak admin " + operation + " : identifiant absent");
        }
        return id;
    }

    private static void requireUuid(String id) {
        if (id == null || !CANONICAL_UUID.matcher(id).matches()) {
            throw new IllegalArgumentException("Identifiant Keycloak invalide");
        }
    }
}
