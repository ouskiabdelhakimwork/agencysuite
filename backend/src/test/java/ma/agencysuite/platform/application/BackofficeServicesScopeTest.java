package ma.agencysuite.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.platform.domain.SubscriptionPeriod;
import ma.agencysuite.shared.module.ModuleCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Les services du back-office ne s'executent que dans le perimetre plateforme : en perimetre
 * agence ou sans perimetre, erreur de programmation (500) et aucune ecriture.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackofficeServicesScopeTest {

    @Autowired
    private AgencyAdministration administration;
    @Autowired
    private ModuleActivation moduleActivation;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("svc-" + suffix, ModuleCode.MICE);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void agencyAdministrationRefusesAnAgencyScope() {
        AgencyId own = new AgencyId(agencyId);

        assertThatThrownBy(() -> TenantContext.callAsAgency(own, () -> administration.createAgency(command("in-agency"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TenantContext.callAsAgency(own, () -> administration.getAgency(agencyId)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(data.agencyExists("in-agency-" + suffix)).isFalse();
    }

    @Test
    void agencyAdministrationRefusesNoScope() {
        assertThatThrownBy(() -> administration.createAgency(command("no-scope")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> administration.getAgency(agencyId)).isInstanceOf(IllegalStateException.class);
        assertThat(data.agencyExists("no-scope-" + suffix)).isFalse();
    }

    @Test
    void moduleActivationRefusesAnAgencyScopeEvenForItsOwnAgency() {
        AgencyId own = new AgencyId(agencyId);

        assertThatThrownBy(() -> TenantContext.runAsAgency(own, () -> moduleActivation.enable(agencyId, ModuleCode.TRAVEL)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TenantContext.runAsAgency(own, () -> moduleActivation.disable(agencyId, ModuleCode.MICE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.agencyModule(agencyId, ModuleCode.TRAVEL)).isEmpty();
        assertThat(data.agencyModule(agencyId, ModuleCode.MICE)).hasValueSatisfying(m -> assertThat(m.isEnabled()).isTrue());
    }

    @Test
    void moduleActivationRefusesNoScope() {
        assertThatThrownBy(() -> moduleActivation.enable(agencyId, ModuleCode.TRAVEL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> moduleActivation.disable(agencyId, ModuleCode.MICE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.agencyModule(agencyId, ModuleCode.TRAVEL)).isEmpty();
        assertThat(data.agencyModule(agencyId, ModuleCode.MICE)).hasValueSatisfying(m -> assertThat(m.isEnabled()).isTrue());
    }

    private CreateAgencyCommand command(String prefix) {
        return new CreateAgencyCommand("Agence " + prefix, prefix + "-" + suffix, null, null, null, null, null, null,
                "STARTER", SubscriptionPeriod.MONTHLY, LocalDate.of(2026, 12, 1), List.of(ModuleCode.TRAVEL));
    }
}
