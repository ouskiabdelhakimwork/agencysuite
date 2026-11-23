package ma.agencysuite.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgencyTest {

    @Test
    void newCustomerAgencyStartsInTrialWithMoroccanDefaults() {
        Agency agency = Agency.createCustomer("  Atlas Voyages ", "Atlas-Voyages");

        assertThat(agency.getName()).isEqualTo("Atlas Voyages");
        assertThat(agency.getSubdomain()).isEqualTo("atlas-voyages");
        assertThat(agency.getStatus()).isEqualTo(AgencyStatus.TRIAL);
        assertThat(agency.getKind()).isEqualTo(AgencyKind.CUSTOMER);
        assertThat(agency.getDefaultLocale()).isEqualTo("fr");
        assertThat(agency.getDefaultCurrency()).isEqualTo("MAD");
        assertThat(agency.getTimezone()).isEqualTo("Africa/Casablanca");
        assertThat(agency.getId()).isNotNull();
        assertThat(agency.isOperational()).isTrue();
    }

    @Test
    void demoAgencyIsActiveImmediately() {
        Agency demo = Agency.createDemo("Agence de demonstration", "demo-atlas");

        assertThat(demo.getKind()).isEqualTo(AgencyKind.DEMO);
        assertThat(demo.getStatus()).isEqualTo(AgencyStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "-atlas", "atlas-", "atlas voyages", "atlas_voyages", "www", "admin", "api"})
    void rejectsInvalidOrReservedSubdomains(String subdomain) {
        assertThatThrownBy(() -> Agency.createCustomer("Atlas", subdomain))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAName() {
        assertThatThrownBy(() -> Agency.createCustomer(" ", "atlas")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suspendedAgencyIsNotOperationalUntilReactivated() {
        Agency agency = Agency.createCustomer("Atlas", "atlas");

        agency.suspend();
        assertThat(agency.isOperational()).isFalse();

        agency.activate();
        assertThat(agency.isOperational()).isTrue();
    }
}
