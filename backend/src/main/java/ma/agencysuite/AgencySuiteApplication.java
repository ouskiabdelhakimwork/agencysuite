package ma.agencysuite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree du monolithe modulaire AgencySuite.
 *
 * <p>Chaque sous-paquet direct de {@code ma.agencysuite} est un module metier
 * (voir 03-decisions-architecture.md, section 2). Les frontieres sont verifiees
 * par Spring Modulith dans {@code ModularityTests}.
 */
@SpringBootApplication
public class AgencySuiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgencySuiteApplication.class, args);
    }
}
