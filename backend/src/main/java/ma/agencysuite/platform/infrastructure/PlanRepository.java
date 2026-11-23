package ma.agencysuite.platform.infrastructure;

import ma.agencysuite.platform.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {
}
