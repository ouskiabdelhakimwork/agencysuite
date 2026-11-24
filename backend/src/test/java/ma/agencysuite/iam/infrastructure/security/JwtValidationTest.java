package ma.agencysuite.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Validateurs appliques par les decodeurs apres la verification de signature. */
class JwtValidationTest {

    private static final String ISSUER = "https://keycloak.test/realms/agencysuite";
    private static final String OTHER_ISSUER = "https://keycloak.test/realms/platform";
    private static final String AUDIENCE = "agencysuite-api";

    private final OAuth2TokenValidator<Jwt> validator = KeycloakJwtDecoders.validator(ISSUER, AUDIENCE);

    @Test
    void acceptsTheRightIssuerAndAudience() {
        assertThat(validator.validate(token(jwt -> { })).hasErrors()).isFalse();
        assertThat(validator.validate(token(jwt -> jwt.audience(List.of("account", AUDIENCE)))).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherIssuer() {
        assertThat(validator.validate(token(jwt -> jwt.issuer(OTHER_ISSUER))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAMissingOrDifferentAudience() {
        assertThat(validator.validate(token(jwt -> jwt.claims(claims -> claims.remove("aud")))).hasErrors()).isTrue();
        assertThat(validator.validate(token(jwt -> jwt.audience(List.of("account")))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        assertThat(validator.validate(token(jwt -> jwt.issuedAt(past.minusSeconds(300)).expiresAt(past))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsATokenNotYetValid() {
        Instant future = Instant.now().plusSeconds(3600);
        assertThat(validator.validate(token(jwt -> jwt.notBefore(future))).hasErrors()).isTrue();
    }

    /** Jeton valide par defaut (forme d'un jeton d'acces Keycloak), modifie par {@code changes}. */
    private static Jwt token(Consumer<Jwt.Builder> changes) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject("f3b2c1d0-0000-4000-8000-000000000001")
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300));
        changes.accept(builder);
        return builder.build();
    }
}
