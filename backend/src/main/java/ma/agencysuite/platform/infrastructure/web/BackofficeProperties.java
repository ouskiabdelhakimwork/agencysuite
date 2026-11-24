package ma.agencysuite.platform.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration provisoire du back-office editeur.
 *
 * @param unsecuredAccess {@code true} ouvre le back-office SANS authentification (tests et poste
 *        local uniquement, jamais dans application.yml principal). Par defaut {@code false} :
 *        tout appel sous /api/v1/backoffice/** est refuse (403 backoffice-disabled).
 */
// TODO(etape-3) : supprimer cette propriete et verifier le jeton du realm Keycloak "platform".
@ConfigurationProperties("agencysuite.backoffice")
public record BackofficeProperties(boolean unsecuredAccess) {
}
