package ma.agencysuite;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Jetons Keycloak simules pour MockMvc : la requete passe par les vraies chaines de securite
 * (regles d'autorisation, ouverture du perimetre, resolution de l'utilisateur), seule la
 * verification de signature est evitee.
 *
 * <p>Les claims reprennent la forme observee sur Keycloak 26.6 (etape 3, lot 1) :
 * {@code "organization": ["<alias>"]}, liste des alias des Organizations de l'utilisateur,
 * l'alias etant l'UUID de l'agence. Les issuers sont ceux de src/test/resources/config/application.yml.
 *
 * <p>Utilisateurs (etape 3, lot 2) : un jeton valide ne suffit plus, son {@code sub} doit etre
 * celui d'un utilisateur ACTIVE.
 * <ul>
 *   <li>{@link #agency(UUID)} porte le sub du gerant par defaut de l'agence ({@link #managerSub(UUID)}),
 *       cree par {@code PlatformTestData.createAgency} ;</li>
 *   <li>{@link #platform()} porte le sub du membre ADMIN de l'editeur cree par la migration de
 *       test V1001 ({@link #PLATFORM_ADMIN_SUB}).</li>
 * </ul>
 */
public final class TestTokens {

    public static final String AGENCY_ISSUER = "https://keycloak.test/realms/agencysuite";
    public static final String PLATFORM_ISSUER = "https://keycloak.test/realms/platform";
    public static final String AUDIENCE = "agencysuite-api";

    /** sub du membre ADMIN de l'editeur insere par V1001__insert_test_platform_admin.sql. */
    public static final String PLATFORM_ADMIN_SUB = "7e57ad41-0000-4000-8000-000000000001";

    private TestTokens() {
    }

    /** sub du gerant (MANAGER, ACTIVE) cree avec chaque agence de test. Deterministe. */
    public static String managerSub(UUID agencyId) {
        return UUID.nameUUIDFromBytes(("manager:" + agencyId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Gerant par defaut de l'agence, membre de son Organization. */
    public static JwtRequestPostProcessor agency(UUID agencyId) {
        return agencyUser(agencyId, managerSub(agencyId));
    }

    /** Utilisateur du realm "agencysuite" ayant ce sub, membre de l'Organization de cette agence. */
    public static JwtRequestPostProcessor agencyUser(UUID agencyId, String sub) {
        return jwt().jwt(token -> token
                .issuer(AGENCY_ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(sub)
                .claim("organization", List.of(agencyId.toString())));
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

    /** Membre ADMIN actif de l'editeur (realm "platform"). */
    public static JwtRequestPostProcessor platform() {
        return platformUser(PLATFORM_ADMIN_SUB);
    }

    /** Jeton du realm "platform" ayant ce sub. */
    public static JwtRequestPostProcessor platformUser(String sub) {
        return jwt().jwt(token -> token
                .issuer(PLATFORM_ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(sub));
    }
}
