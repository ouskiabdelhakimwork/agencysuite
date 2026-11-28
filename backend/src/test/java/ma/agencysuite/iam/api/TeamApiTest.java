package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.TestTokens.platform;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Annuaire de l'equipe (GET /api/v1/team) : ouvert a tous les roles de l'agence, utilisateurs
 * actifs et invites seulement, ni e-mail ni roles, jamais un utilisateur d'une autre agence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamApiTest {

    private static final String TEAM = "/api/v1/team";

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
    void createAgencies() {
        agencyA = data.createAgency("team-a-" + suffix);
        agencyB = data.createAgency("team-b-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void everyRoleReadsTheTeam() throws Exception {
        for (String role : new String[] {"MANAGER", "SALES", "OPERATIONS", "ACCOUNTANT"}) {
            String sub = iam.createUser(agencyA, UserStatus.ACTIVE, role);
            mvc.perform(get(TEAM).with(agencyUser(agencyA, sub)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id").value(hasItem(iam.userId(agencyA, sub).toString())));
        }
    }

    @Test
    void onlyActiveAndInvitedUsersAreListedWithoutEmailNorRoles() throws Exception {
        UUID manager = iam.userId(agencyA, TestTokens.managerSub(agencyA));
        UUID invited = iam.userId(agencyA, iam.createUser(agencyA, UserStatus.INVITED, "SALES"));
        String disabledSub = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");
        UUID disabled = iam.userId(agencyA, disabledSub);
        iam.disableUser(agencyA, disabledSub);

        mvc.perform(get(TEAM).with(agency(agencyA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(manager.toString(), invited.toString())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(disabled.toString()))))
                .andExpect(jsonPath("$[*].status").value(containsInAnyOrder("ACTIVE", "INVITED")))
                .andExpect(jsonPath("$[*].fullName").value(everyItem(org.hamcrest.Matchers.startsWith("Utilisateur "))))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].roles").doesNotExist())
                .andExpect(jsonPath("$[0].lastLoginAt").doesNotExist())
                .andExpect(jsonPath("$[1].email").doesNotExist());
    }

    @Test
    void theTeamIsSortedByName() throws Exception {
        iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");
        iam.createUser(agencyA, UserStatus.ACTIVE, "SALES");

        String body = mvc.perform(get(TEAM).with(agency(agencyA))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> names = com.jayway.jsonpath.JsonPath.read(body, "$[*].fullName");
        org.assertj.core.api.Assertions.assertThat(names).hasSize(3)
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void anotherAgencysUsersAreNeverListed() throws Exception {
        UUID managerOfB = iam.userId(agencyB, TestTokens.managerSub(agencyB));
        UUID managerOfA = iam.userId(agencyA, TestTokens.managerSub(agencyA));

        mvc.perform(get(TEAM).with(agency(agencyA)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(managerOfB.toString()))));
        mvc.perform(get(TEAM).with(agency(agencyB)))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(managerOfB.toString())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(managerOfA.toString()))));
    }

    @Test
    void aPlatformTokenIsRefusedLikeOnAnyAgencyRoute() throws Exception {
        mvc.perform(get(TEAM).with(platform())).andExpect(status().isForbidden());
        mvc.perform(get(TEAM)).andExpect(status().isUnauthorized());
    }
}
