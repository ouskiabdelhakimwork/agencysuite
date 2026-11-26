package ma.agencysuite.iam.api;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.agencyUser;
import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestTokens;
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

/** POST /api/v1/me/login-events : derniere connexion et entree USER_LOGGED_IN. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginEventApiTest {

    private static final String LOGIN_EVENTS = "/api/v1/me/login-events";

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
        agencyId = data.createAgency("login-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void recordsTheLoginAndUpdatesLastLoginAt() throws Exception {
        String sub = TestTokens.managerSub(agencyId);
        UUID userId = iam.userId(agencyId, sub);
        assertThat(iam.lastLoginAt(agencyId, sub)).isNull();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        mvc.perform(post(LOGIN_EVENTS).with(agency(agencyId))).andExpect(status().isNoContent());

        assertThat(iam.lastLoginAt(agencyId, sub)).isAfterOrEqualTo(before);
        List<Row> entries = audit.entries(agencyId);
        assertThat(entries).hasSize(1);
        Row entry = entries.getFirst();
        assertThat(entry.action()).isEqualTo("USER_LOGGED_IN");
        assertThat(entry.entityType()).isEqualTo("USER");
        assertThat(entry.entityId()).isEqualTo(userId.toString());
        assertThat(entry.actorType()).isEqualTo("AGENCY_USER");
        assertThat(entry.actorId()).isEqualTo(userId);
        assertThat(entry.details()).isEqualTo("{}");
        assertThat(entry.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void aSecondLoginWithinSixtySecondsIsIgnored() throws Exception {
        String sub = TestTokens.managerSub(agencyId);
        mvc.perform(post(LOGIN_EVENTS).with(agency(agencyId))).andExpect(status().isNoContent());
        Instant first = iam.lastLoginAt(agencyId, sub);

        mvc.perform(post(LOGIN_EVENTS).with(agency(agencyId))).andExpect(status().isNoContent());

        assertThat(audit.entries(agencyId)).hasSize(1);
        assertThat(iam.lastLoginAt(agencyId, sub)).isEqualTo(first);
    }

    @Test
    void eachUserHasItsOwnLimit() throws Exception {
        String other = iam.createUser(agencyId, UserStatus.ACTIVE, "SALES");

        mvc.perform(post(LOGIN_EVENTS).with(agency(agencyId))).andExpect(status().isNoContent());
        mvc.perform(post(LOGIN_EVENTS).with(agencyUser(agencyId, other))).andExpect(status().isNoContent());

        assertThat(audit.entries(agencyId)).extracting(Row::actorId)
                .containsExactly(iam.userId(agencyId, TestTokens.managerSub(agencyId)), iam.userId(agencyId, other));
    }

    @Test
    void refusedForADisabledUserAndForThePlatform() throws Exception {
        String disabled = iam.createUser(agencyId, UserStatus.DISABLED, "SALES");

        mvc.perform(post(LOGIN_EVENTS).with(agencyUser(agencyId, disabled))).andExpect(status().isForbidden());
        mvc.perform(post(LOGIN_EVENTS).with(platform())).andExpect(status().isForbidden());
        mvc.perform(post(LOGIN_EVENTS)).andExpect(status().isUnauthorized());

        assertThat(audit.entries(agencyId)).isEmpty();
    }
}
