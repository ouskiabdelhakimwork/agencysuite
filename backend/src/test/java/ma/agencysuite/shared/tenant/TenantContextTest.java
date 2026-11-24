package ma.agencysuite.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private static final AgencyId AGENCY_A = new AgencyId(UUID.randomUUID());
    private static final AgencyId AGENCY_B = new AgencyId(UUID.randomUUID());

    @Test
    void hasNoScopeOutsideABlock() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::requireAgency).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesTheAgencyOnlyInsideTheBlock() {
        AgencyId seen = TenantContext.callAsAgency(AGENCY_A, TenantContext::requireAgency);

        assertThat(seen).isEqualTo(AGENCY_A);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void clearsTheScopeEvenWhenTheBlockFails() {
        assertThatThrownBy(() -> TenantContext.runAsAgency(AGENCY_A, () -> {
            throw new IllegalArgumentException("echec metier");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void innerScopeReplacesOuterScopeThenRestoresIt() {
        TenantContext.runAsAgency(AGENCY_A, () -> {
            assertThat(TenantContext.callAsAgency(AGENCY_B, TenantContext::requireAgency)).isEqualTo(AGENCY_B);
            assertThat(TenantContext.requireAgency()).isEqualTo(AGENCY_A);
        });
    }

    @Test
    void platformScopeHasNoAgency() {
        TenantContext.runAsPlatform(() -> {
            assertThat(TenantContext.current()).containsInstanceOf(TenantScope.Platform.class);
            assertThatThrownBy(TenantContext::requireAgency).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void scopeIsNotSharedWithOtherThreads() throws InterruptedException {
        AtomicReference<Optional<TenantScope>> seenByOtherThread = new AtomicReference<>();
        Thread other = Thread.ofVirtual().unstarted(() -> seenByOtherThread.set(TenantContext.current()));

        TenantContext.runAsAgency(AGENCY_A, other::start);
        other.join();

        // Une tache lancee depuis un perimetre n'en herite pas : elle doit ouvrir le sien.
        assertThat(seenByOtherThread.get()).isEmpty();
    }
}
