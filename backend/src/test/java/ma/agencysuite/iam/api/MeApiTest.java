package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.TestTokens.platform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/v1/me (utilisateur d'agence) et GET /api/v1/backoffice/me (membre de l'editeur). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeApiTest {

    private static final String ME = "/api/v1/me";
    private static final String BACKOFFICE_ME = "/api/v1/backoffice/me";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createTwoAgencies() {
        agencyA = data.createAgency("me-a-" + suffix);
        agencyB = data.createAgency("me-b-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void managerSeesItsProfileItsAgencyAndAllPermissions() throws Exception {
        String sub = TestTokens.managerSub(agencyA);

        mvc.perform(get(ME).with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user-" + sub.substring(0, 8) + "@example.test"))
                .andExpect(jsonPath("$.fullName").value("Utilisateur " + sub.substring(0, 8)))
                .andExpect(jsonPath("$.agency.id").value(agencyA.toString()))
                .andExpect(jsonPath("$.agency.name").value("Agence me-a-" + suffix))
                .andExpect(jsonPath("$.roles", Matchers.contains("MANAGER")))
                .andExpect(jsonPath("$.permissions", Matchers.contains(
                        "audit.read", "billing.issue", "crm.write", "identity.read", "privacy.manage",
                        "supplier.write", "ticketing.write", "user.manage")));
    }

    @Test
    void permissionsAreTheUnionOfTheRoles() throws Exception {
        String sub = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES", "OPERATIONS");

        mvc.perform(get(ME).with(agencyUser(agencyA, sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", Matchers.contains("OPERATIONS", "SALES")))
                .andExpect(jsonPath("$.permissions", Matchers.contains("crm.write", "identity.read",
                        "supplier.write", "ticketing.write")));
    }

    @Test
    void eachUserSeesOnlyItsOwnDataAndAgency() throws Exception {
        mvc.perform(get(ME).with(agency(agencyB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agency.id").value(agencyB.toString()))
                .andExpect(jsonPath("$.agency.name").value("Agence me-b-" + suffix))
                .andExpect(jsonPath("$.email").value(
                        "user-" + TestTokens.managerSub(agencyB).substring(0, 8) + "@example.test"));
        // Le gerant de B avec un jeton designant A : refuse, rien de A ni de B n'est renvoye.
        mvc.perform(get(ME).with(agencyUser(agencyA, TestTokens.managerSub(agencyB))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void platformTokenIsRefusedOnMe() throws Exception {
        mvc.perform(get(ME).with(platform())).andExpect(status().isForbidden());
    }

    @Test
    void backofficeMeReturnsThePlatformUser() throws Exception {
        mvc.perform(get(BACKOFFICE_ME).with(platform()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("7e57ad41-0000-7000-8000-000000000001"))
                .andExpect(jsonPath("$.email").value("admin@example.test"))
                .andExpect(jsonPath("$.fullName").value("Admin Test"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void agencyTokenIsRefusedOnBackofficeMe() throws Exception {
        mvc.perform(get(BACKOFFICE_ME).with(agency(agencyA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
    }
}
