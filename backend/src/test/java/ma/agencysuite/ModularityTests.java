package ma.agencysuite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
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

    /** Etape 5, lot 1 : le CRM ne s'appuie que sur le noyau partage, et iam ne connait pas le CRM. */
    @Test
    void crmDependsOnlyOnSharedAndIamDoesNotDependOnCrm() {
        assertThat(directDependencies("crm")).containsExactly("shared");
        assertThat(directDependencies("iam")).doesNotContain("crm");
    }

    /**
     * Etape 5, lot 3 : supplier ne s'appuie que sur le noyau partage (ni crm ni iam), et crm reste
     * limite a shared apres le deplacement de SearchKeys.
     */
    @Test
    void supplierDependsOnlyOnSharedAndIamDoesNotDependOnSupplier() {
        assertThat(directDependencies("supplier")).containsExactly("shared");
        assertThat(directDependencies("crm")).containsExactly("shared");
        assertThat(directDependencies("iam")).doesNotContain("supplier");
    }

    private static java.util.List<String> directDependencies(String moduleName) {
        ApplicationModule module = MODULES.getModuleByName(moduleName).orElseThrow();
        return module.getDirectDependencies(MODULES).uniqueModules()
                .map(dependency -> dependency.getIdentifier().toString())
                .distinct().sorted().toList();
    }

    /** Genere les diagrammes des modules dans target/spring-modulith-docs (PlantUML). */
    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
