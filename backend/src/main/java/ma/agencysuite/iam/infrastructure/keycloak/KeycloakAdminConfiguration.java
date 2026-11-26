package ma.agencysuite.iam.infrastructure.keycloak;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import ma.agencysuite.iam.application.IdentityProviderAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Adaptateur de l'API d'administration Keycloak : delais courts (connexion 5 s, lecture 10 s). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class KeycloakAdminConfiguration {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminConfiguration.class);

    @Bean
    IdentityProviderAdmin keycloakIdentityProviderAdmin(KeycloakAdminProperties properties) {
        if (!properties.configured()) {
            LOG.warn("Secret du compte de service Keycloak absent (KEYCLOAK_BACKEND_CLIENT_SECRET) : "
                    + "les invitations et la gestion des utilisateurs repondront 503");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new KeycloakIdentityProviderAdmin(properties, RestClient.builder().requestFactory(requestFactory),
                Clock.systemUTC());
    }
}
