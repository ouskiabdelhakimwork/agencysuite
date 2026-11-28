package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agencyUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
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

/** Premiere connexion d'un invite : acces autorise, puis ACTIVE au premier login-events. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FirstLoginTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("first-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void anInvitedUserIsAllowedAndBecomesActiveAtItsFirstLoginEvent() throws Exception {
        String sub = iam.createUser(agencyId, UserStatus.INVITED, "SALES");
        UUID userId = iam.userId(agencyId, sub);

        mvc.perform(get("/api/v1/me").with(agencyUser(agencyId, sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SALES"));
        assertThat(iam.status(agencyId, userId)).isEqualTo(UserStatus.INVITED);

        mvc.perform(post("/api/v1/me/login-events").with(agencyUser(agencyId, sub))).andExpect(status().isNoContent());

        assertThat(iam.status(agencyId, userId)).isEqualTo(UserStatus.ACTIVE);
        assertThat(iam.lastLoginAt(agencyId, sub)).isNotNull();
        List<Row> entries = audit.entries(agencyId);
        // Ecrites dans la meme transaction : ordre non garanti.
        assertThat(entries).extracting(Row::action).containsExactlyInAnyOrder("USER_ACTIVATED", "USER_LOGGED_IN");
        assertThat(entries).allSatisfy(row -> {
            assertThat(row.entityId()).isEqualTo(userId.toString());
            assertThat(row.actorType()).isEqualTo("AGENCY_USER");
            assertThat(row.actorId()).isEqualTo(userId);
            assertThat(row.details()).isEqualTo("{}");
        });
    }

    @Test
    void anActiveUserIsNotActivatedAgain() throws Exception {
        String sub = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");

        mvc.perform(post("/api/v1/me/login-events").with(agencyUser(agencyId, sub))).andExpect(status().isNoContent());

        assertThat(audit.entries(agencyId)).extracting(Row::action).containsExactly("USER_LOGGED_IN");
    }

    @Test
    void aDisabledUserIsStillRefused() throws Exception {
        String sub = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");
        iam.disableUser(agencyId, sub);

        mvc.perform(get("/api/v1/me").with(agencyUser(agencyId, sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:access-denied"));
        mvc.perform(post("/api/v1/me/login-events").with(agencyUser(agencyId, sub))).andExpect(status().isForbidden());
        assertThat(audit.entries(agencyId)).isEmpty();
    }
}
