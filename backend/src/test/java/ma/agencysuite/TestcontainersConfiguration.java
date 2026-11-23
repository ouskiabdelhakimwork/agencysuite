package ma.agencysuite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base PostgreSQL jetable pour les tests d'integration (Testcontainers 2).
 * Importer avec @Import(TestcontainersConfiguration.class) : Spring Boot branche
 * automatiquement la source de donnees sur le conteneur (@ServiceConnection),
 * puis Flyway joue les migrations comme en production.
 *
 * <p>Le contexte Spring etant mis en cache entre les classes de test, un seul
 * conteneur est demarre pour toute la suite. Necessite Docker (Rancher Desktop en
 * local, disponible nativement dans GitHub Actions).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
