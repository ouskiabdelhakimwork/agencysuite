package ma.agencysuite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Jetons Keycloak simules pour MockMvc : la requete passe par les vraies chaines de securite
 * (regles d'autorisation, ouverture du perimetre), seule la verification de signature est evitee.
 *
 * <p>Les claims reprennent la forme observee sur Keycloak 26.6 (etape 3, lot 1) :
 * {@code "organization": ["<alias>"]}, liste des alias des Organizations de l'utilisateur,
 * l'alias etant l'UUID de l'agence. Les issuers sont ceux de src/test/resources/config/application.yml.
 */
public final class TestTokens {

    public static final String AGENCY_ISSUER = "https://keycloak.test/realms/agencysuite";
    public static final String PLATFORM_ISSUER = "https://keycloak.test/realms/platform";
    public static final String AUDIENCE = "agencysuite-api";

    private TestTokens() {
    }

    /** Utilisateur du realm "agencysuite", membre de l'Organization de cette agence. */
    public static JwtRequestPostProcessor agency(UUID agencyId) {
        return agencyWithOrganizations(List.of(agencyId.toString()));
    }

    /** Utilisateur du realm "agencysuite" avec le claim organization donne tel quel. */
    public static JwtRequestPostProcessor agencyWithOrganizations(List<String> aliases) {
        return jwt().jwt(token -> token
                .issuer(AGENCY_ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString())
                .claim("organization", aliases));
    }

    /** Utilisateur du realm "agencysuite" sans claim organization. */
    public static JwtRequestPostProcessor agencyWithoutOrganization() {
        return jwt().jwt(token -> token
                .issuer(AGENCY_ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString()));
    }

    /** Membre de l'editeur (realm "platform"). */
    public static JwtRequestPostProcessor platform() {
        return jwt().jwt(token -> token
                .issuer(PLATFORM_ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(UUID.randomUUID().toString()));
    }
}
