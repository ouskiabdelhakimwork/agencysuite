package ma.agencysuite;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifie les frontieres entre modules (ADR-01) : un module n'utilise que les
 * modules declares dans allowedDependencies de son package-info.java, et jamais
 * les classes internes d'un autre module. Echoue au build en cas de violation.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(AgencySuiteApplication.class);

    @Test
    void respectsModuleBoundaries() {
        MODULES.verify();
    }

    /** Genere les diagrammes des modules dans target/spring-modulith-docs (PlantUML). */
    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
