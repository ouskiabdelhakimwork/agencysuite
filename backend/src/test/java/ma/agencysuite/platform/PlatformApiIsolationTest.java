package ma.agencysuite.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import ma.agencysuite.TestcontainersConfiguration;
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
 * {@link PlatformApi} ne lit jamais une autre agence que l'agence courante : dans le perimetre
 * de A, les modules de B sont invisibles. Seul le perimetre plateforme, deja ouvert par
 * l'appelant, voit toutes les agences.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlatformApiIsolationTest {

    @Autowired
    private PlatformApi platform;
    @Autowired
    private PlatformTestData data;

    private final String suffix = PlatformTestData.randomSuffix();
    private AgencyId agencyA;
    private AgencyId agencyB;

    @BeforeEach
    void createTwoAgencies() {
        agencyA = new AgencyId(data.createAgency("api-a-" + suffix, ModuleCode.TRAVEL));
        agencyB = new AgencyId(data.createAgency("api-b-" + suffix, ModuleCode.MICE, ModuleCode.GIFTBOX));
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void agencyAGetsNoDataAboutAgencyB() {
        boolean bHasMiceSeenByA = TenantContext.callAsAgency(agencyA, () -> platform.isModuleEnabled(agencyB, ModuleCode.MICE));
        Set<ModuleCode> modulesOfBSeenByA = TenantContext.callAsAgency(agencyA, () -> platform.enabledModules(agencyB));

        assertThat(bHasMiceSeenByA).isFalse();
        assertThat(modulesOfBSeenByA).isEmpty();
    }

    @Test
    void agencyReadsItsOwnModules() {
        assertThat(TenantContext.callAsAgency(agencyA, () -> platform.isModuleEnabled(agencyA, ModuleCode.TRAVEL))).isTrue();
        assertThat(TenantContext.callAsAgency(agencyA, () -> platform.enabledModules(agencyA)))
                .containsExactly(ModuleCode.TRAVEL);
    }

    @Test
    void platformScopeAlreadyOpenSeesEveryAgency() {
        assertThat(TenantContext.callAsPlatform(() -> platform.enabledModules(agencyB)))
                .containsExactlyInAnyOrder(ModuleCode.MICE, ModuleCode.GIFTBOX);
        assertThat(TenantContext.callAsPlatform(() -> platform.isModuleEnabled(agencyA, ModuleCode.TRAVEL))).isTrue();
    }

    @Test
    void withoutAScopeNothingIsVisible() {
        assertThat(platform.isModuleEnabled(agencyA, ModuleCode.TRAVEL)).isFalse();
        assertThat(platform.enabledModules(agencyB)).isEmpty();
    }

    @Test
    void agencyNameFollowsTheSameScopeRule() {
        assertThat(TenantContext.callAsAgency(agencyA, () -> platform.agencyName(agencyA)))
                .contains("Agence api-a-" + suffix);
        assertThat(TenantContext.callAsAgency(agencyA, () -> platform.agencyName(agencyB))).isEmpty();
        assertThat(TenantContext.callAsPlatform(() -> platform.agencyName(agencyB))).contains("Agence api-b-" + suffix);
        assertThat(platform.agencyName(agencyA)).isEmpty();
    }

    @Test
    void unknownAgencyHasNoModules() {
        AgencyId unknown = new AgencyId(UUID.randomUUID());

        assertThat(TenantContext.callAsAgency(unknown, () -> platform.enabledModules(unknown))).isEmpty();
    }
}
