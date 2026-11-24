package ma.agencysuite.iam.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodeurs de jetons, un par realm Keycloak.
 *
 * <ul>
 *   <li>Signature verifiee avec les cles publiques du realm ({@code <issuer>/protocol/openid-connect/certs}),
 *       chargees a la premiere requete (l'application et les tests demarrent sans Keycloak) ;
 *       RS256 uniquement (algorithme par defaut des cles Keycloak), jamais de jeton non signe.</li>
 *   <li>Validateurs : ceux par defaut (expiration, date de debut, type), issuer exact du realm,
 *       audience contenant {@code agencysuite-api}.</li>
 * </ul>
 */
final class KeycloakJwtDecoders {

    private KeycloakJwtDecoders() {
    }

    static JwtDecoder forRealm(String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    /** Validateurs appliques apres la verification de signature (expose pour les tests unitaires). */
    static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience));
    }
}
