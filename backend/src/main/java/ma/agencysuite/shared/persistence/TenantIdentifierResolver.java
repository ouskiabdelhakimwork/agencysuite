package ma.agencysuite.shared.persistence;

import java.util.UUID;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.shared.tenant.TenantScope;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Indique a Hibernate l'agence courante, lue dans {@link TenantContext}.
 *
 * <ul>
 *   <li>perimetre agence : l'identifiant de l'agence ;</li>
 *   <li>perimetre plateforme : {@link #PLATFORM}, tenant « racine » qui voit tout ;</li>
 *   <li>aucun perimetre : {@link #NO_TENANT}, qui ne correspond a aucune agence. Les
 *       lectures ne renvoient rien et les ecritures echouent : un oubli ne fuit jamais.</li>
 * </ul>
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /** Aucune agence : UUID nul (00000000-0000-0000-0000-000000000000). */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    /** Plateforme : UUID maximal (ffffffff-ffff-ffff-ffff-ffffffffffff), jamais attribue a une agence. */
    public static final UUID PLATFORM = new UUID(-1L, -1L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current()
                .map(scope -> switch (scope) {
                    case TenantScope.Agency agency -> agency.agencyId().value();
                    case TenantScope.Platform platform -> PLATFORM;
                })
                .orElse(NO_TENANT);
    }

    /** Une session ouverte pour une agence ne peut pas etre reutilisee pour une autre. */
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return PLATFORM.equals(tenantId);
    }
}
