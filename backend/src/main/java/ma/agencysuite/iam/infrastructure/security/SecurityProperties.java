package ma.agencysuite.iam.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Emetteurs Keycloak acceptes (adresses publiques, pas des secrets).
 *
 * @param agencyIssuer issuer exact du realm des agences ("agencysuite")
 * @param platformIssuer issuer exact du realm de l'editeur ("platform"), seul accepte sur le back-office
 * @param audience audience que tout jeton doit contenir (mapper d'audience des clients Keycloak)
 */
@Validated
@ConfigurationProperties("agencysuite.security")
public record SecurityProperties(
        @NotBlank String agencyIssuer,
        @NotBlank String platformIssuer,
        @NotBlank String audience) {
}
