package ma.agencysuite.iam.application;

import static ma.agencysuite.TestTokens.agency;
import static ma.agencysuite.TestTokens.platform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.module.ModuleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Aucune donnee personnelle dans {@code details} : ni e-mail ({@code @}) ni nom des utilisateurs de
 * test ({@code IamTestData} : « Utilisateur … », « Equipe Test », V1001 : « Admin Test »). Toutes les
 * actions tracees de ce lot sont produites, puis tout le journal present en base est controle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditPrivacyTest {

    private static final List<String> PERSONAL_MARKERS =
            List.of("@", "Utilisateur", "Equipe Test", "Admin Test", "example.test");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private AuditTestData audit;

    private final String suffix = PlatformTestData.randomSuffix();

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void detailsNeverContainPersonalData() throws Exception {
        UUID created = AuditTrailTest.createAgency(mvc, "priv-" + suffix);
        mvc.perform(delete(AuditTrailTest.module(created, "TRAVEL")).with(platform()))
                .andExpect(status().isNoContent());
        UUID withManager = data.createAgency("priv-m-" + suffix, ModuleCode.MICE);
        mvc.perform(post("/api/v1/me/login-events").with(agency(withManager))).andExpect(status().isNoContent());

        List<Row> produced = new java.util.ArrayList<>(audit.entries(created));
        produced.addAll(audit.entries(withManager));
        assertThat(produced).extracting(Row::action).contains("AGENCY_CREATED", "MODULE_ENABLED",
                "MODULE_DISABLED", "USER_LOGGED_IN");

        assertThat(audit.allEntries()).allSatisfy(row -> {
            for (String marker : PERSONAL_MARKERS) {
                assertThat(row.details()).as("details de %s", row.action()).doesNotContain(marker);
                assertThat(row.entityId() == null ? "" : row.entityId()).doesNotContain(marker);
            }
        });
    }
}
