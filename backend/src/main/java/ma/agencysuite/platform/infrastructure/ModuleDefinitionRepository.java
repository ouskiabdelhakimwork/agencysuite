package ma.agencysuite.platform.infrastructure;

import ma.agencysuite.platform.domain.ModuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleDefinitionRepository extends JpaRepository<ModuleDefinition, String> {
}
