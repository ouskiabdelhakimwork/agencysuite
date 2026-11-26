package ma.agencysuite.iam.infrastructure.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Acces a l'API d'administration Keycloak par le compte de service du client confidentiel
 * {@code agencysuite-backend} (realm agencysuite).
 *
 * <p>Le secret vient de l'environnement ({@code KEYCLOAK_BACKEND_CLIENT_SECRET}, en local depuis
 * deploy/.env), jamais du depot. Vide : l'application demarre, les operations d'invitation repondent
 * 503 identity-admin-unavailable. {@link #toString()} ne l'affiche jamais.
 *
 * @param baseUrl adresse de Keycloak (sans /realms)
 * @param invitationLifespan duree de validite du lien d'invitation
 * @param appBaseUrl adresse de l'application web (retour apres l'invitation)
 */
@ConfigurationProperties("agencysuite.keycloak-admin")
record KeycloakAdminProperties(String baseUrl, String realm, String clientId, String clientSecret,
        Duration invitationLifespan, String appBaseUrl) {

    KeycloakAdminProperties {
        invitationLifespan = invitationLifespan == null ? Duration.ofHours(72) : invitationLifespan;
    }

    boolean configured() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        return "KeycloakAdminProperties[baseUrl=" + baseUrl + ", realm=" + realm + ", clientId=" + clientId
                + ", clientSecret=" + (configured() ? "<defini>" : "<absent>") + ", invitationLifespan="
                + invitationLifespan + ", appBaseUrl=" + appBaseUrl + "]";
    }
}
