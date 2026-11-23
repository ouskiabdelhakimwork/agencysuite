package ma.agencysuite.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidsTest {

    @Test
    void generatesVersion7RfcVariantUuids() {
        UUID uuid = Uuids.v7();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void encodesTheTimestampInTheFirst48Bits() {
        long millis = 1_795_000_000_000L;

        UUID uuid = Uuids.v7(millis);

        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void sortsByCreationTime() {
        UUID earlier = Uuids.v7(1_795_000_000_000L);
        UUID later = Uuids.v7(1_795_000_000_001L);

        assertThat(earlier).isLessThan(later);
    }

    @Test
    void isUniqueAcrossManyCalls() {
        Set<UUID> generated = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            generated.add(Uuids.v7());
        }
        assertThat(generated).hasSize(100_000);
    }
}
