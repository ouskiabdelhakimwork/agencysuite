package ma.agencysuite.iam.infrastructure;

import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.iam.domain.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

/** Equipe de l'editeur : lisible uniquement dans le perimetre plateforme (RLS platform_only). */
public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByExternalIdpId(String externalIdpId);
}
