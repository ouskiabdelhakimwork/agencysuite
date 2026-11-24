package ma.agencysuite.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Donnees de reference des droits (migration V4) : alignees sur le code, MANAGER a tout, et
 * l'application ne peut pas les modifier (seule une migration le peut).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReferenceDataTest {

    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private EntityManager entityManager;

    @Test
    void permissionCodeMatchesThePermissionTableExactly() {
        List<String> inDatabase = strings("SELECT code FROM permission ORDER BY code");

        assertThat(inDatabase).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(PermissionCode.values()).map(PermissionCode::code).toList());
    }

    @Test
    void systemRolesAreTheFourDefaultRoles() {
        assertThat(strings("SELECT code FROM system_role"))
                .containsExactlyInAnyOrder("MANAGER", "SALES", "OPERATIONS", "ACCOUNTANT");
    }

    /** Garde-fou pour les migrations futures : toute nouvelle permission doit etre donnee au gerant. */
    @Test
    void managerHasEveryPermission() {
        List<String> all = strings("SELECT code FROM permission");
        List<String> manager = strings("SELECT permission_code FROM system_role_permission WHERE role_code = 'MANAGER'");

        assertThat(manager).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void ticketingPermissionIsLinkedToTheTicketingModule() {
        assertThat(strings("SELECT module_code FROM permission WHERE code = 'ticketing.write'"))
                .containsExactly("TICKETING");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO permission (code) VALUES ('pirate.write')",
        "UPDATE permission SET module_code = NULL WHERE code = 'ticketing.write'",
        "DELETE FROM permission WHERE code = 'audit.read'",
        "INSERT INTO system_role (code) VALUES ('PIRATE')",
        "DELETE FROM system_role WHERE code = 'SALES'",
        "INSERT INTO system_role_permission (role_code, permission_code) VALUES ('SALES', 'audit.read')",
        "UPDATE system_role_permission SET role_code = 'SALES' WHERE role_code = 'MANAGER'",
        "DELETE FROM system_role_permission WHERE role_code = 'MANAGER'",
    })
    void applicationRoleCannotWriteReferenceData(String statement) {
        // Meme dans le perimetre plateforme, le role agencysuite_app n'a que SELECT.
        assertThatThrownBy(() -> asPlatform(() -> entityManager.createNativeQuery(statement).executeUpdate()))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(String sql) {
        return asPlatform(() -> (List<String>) entityManager.createNativeQuery(sql).getResultList());
    }

    private <T> T asPlatform(Supplier<T> work) {
        return TenantContext.callAsPlatform(() -> transaction.execute(status -> work.get()));
    }
}
