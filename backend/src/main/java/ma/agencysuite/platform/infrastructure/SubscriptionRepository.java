package ma.agencysuite.platform.infrastructure;

import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.platform.domain.Subscription;
import ma.agencysuite.platform.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByAgencyIdAndStatus(UUID agencyId, SubscriptionStatus status);
}
