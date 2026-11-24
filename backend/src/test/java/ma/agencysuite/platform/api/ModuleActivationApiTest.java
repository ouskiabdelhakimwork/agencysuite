package ma.agencysuite.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.ModuleDisabled;
import ma.agencysuite.platform.ModuleEnabled;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.platform.domain.AgencyModule;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** Back-office : activation et desactivation d'un module pour une agence (US-102). */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ModuleActivationApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private ApplicationEvents events;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("mods-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void enablesThenDisablesAModuleAndTheAgencyReflectsIt() throws Exception {
        mvc.perform(put(module(agencyId, "TRAVEL"))).andExpect(status().isNoContent());
        mvc.perform(get(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules", Matchers.contains("TRAVEL")));

        mvc.perform(delete(module(agencyId, "TRAVEL"))).andExpect(status().isNoContent());
        mvc.perform(get(agency(agencyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules").isEmpty());

        // Desactiver ne supprime pas la souscription : la ligne reste, inactive.
        AgencyModule row = data.agencyModule(agencyId, ModuleCode.TRAVEL).orElseThrow();
        assertThat(row.isEnabled()).isFalse();
        assertThat(row.getDisabledAt()).isNotNull();
    }

    @Test
    void repeatedCallsAreIdempotentAndPublishOneEventPerStateChange() throws Exception {
        mvc.perform(put(module(agencyId, "GIFTBOX"))).andExpect(status().isNoContent());
        mvc.perform(put(module(agencyId, "GIFTBOX"))).andExpect(status().isNoContent());
        assertThat(enabledEvents(ModuleCode.GIFTBOX)).isEqualTo(1);

        mvc.perform(delete(module(agencyId, "GIFTBOX"))).andExpect(status().isNoContent());
        mvc.perform(delete(module(agencyId, "GIFTBOX"))).andExpect(status().isNoContent());
        assertThat(disabledEvents(ModuleCode.GIFTBOX)).isEqualTo(1);

        mvc.perform(put(module(agencyId, "GIFTBOX"))).andExpect(status().isNoContent());
        assertThat(enabledEvents(ModuleCode.GIFTBOX)).isEqualTo(2);
        mvc.perform(get(agency(agencyId))).andExpect(jsonPath("$.enabledModules", Matchers.contains("GIFTBOX")));
    }

    @Test
    void disablingAModuleNeverSubscribedDoesNothing() throws Exception {
        mvc.perform(delete(module(agencyId, "MICE"))).andExpect(status().isNoContent());

        assertThat(data.agencyModule(agencyId, ModuleCode.MICE)).isEmpty();
        assertThat(events.stream(ModuleDisabled.class)).isEmpty();
    }

    @Test
    void returns404ForAnUnknownAgency() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(put(module(unknown, "TRAVEL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
        mvc.perform(delete(module(unknown, "TRAVEL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
        assertThat(events.stream(ModuleEnabled.class)).isEmpty();
    }

    @Test
    void returns404ForAnUnknownModule() throws Exception {
        mvc.perform(put(module(agencyId, "SPACE_TRAVEL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:agencysuite:problem:not-found"));
        mvc.perform(delete(module(agencyId, "travel")))
                .andExpect(status().isNotFound());
    }

    private long enabledEvents(ModuleCode module) {
        return events.stream(ModuleEnabled.class)
                .filter(e -> e.agencyId().equals(new AgencyId(agencyId)) && e.module() == module)
                .count();
    }

    private long disabledEvents(ModuleCode module) {
        return events.stream(ModuleDisabled.class)
                .filter(e -> e.agencyId().equals(new AgencyId(agencyId)) && e.module() == module)
                .count();
    }

    private static String agency(UUID id) {
        return "/api/v1/backoffice/agencies/" + id;
    }

    private static String module(UUID id, String code) {
        return agency(id) + "/modules/" + code;
    }
}
