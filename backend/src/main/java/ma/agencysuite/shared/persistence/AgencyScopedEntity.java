package ma.agencysuite.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import ma.agencysuite.shared.tenant.AgencyId;
import org.hibernate.annotations.TenantId;

/**
 * Base de toute entite metier appartenant a une agence (ADR-06).
 *
 * <p>{@code @TenantId} : Hibernate remplit agency_id avec l'agence courante a l'insertion
 * et ajoute automatiquement le filtre "agency_id = agence courante" a toutes les requetes.
 * Le code metier ne manipule jamais agency_id lui-meme, et ne peut donc pas l'oublier.
 *
 * <p>Seconde barriere : la table correspondante doit avoir une politique Row Level Security
 * (voir la migration V3 et la regle des conventions) ; un test verifie que toute table
 * portant agency_id en a une.
 */
@MappedSuperclass
public abstract class AgencyScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "agency_id", nullable = false, updatable = false)
    private UUID agencyId;

    public AgencyId getAgencyId() {
        return agencyId == null ? null : new AgencyId(agencyId);
    }
}
