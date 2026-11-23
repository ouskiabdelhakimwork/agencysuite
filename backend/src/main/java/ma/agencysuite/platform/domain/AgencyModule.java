package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ma.agencysuite.shared.module.ModuleCode;

/**
 * Souscription d'une agence a un module (US-102). Desactiver un module ne supprime
 * aucune donnee : la reactivation les rend a nouveau visibles.
 */
@Entity
@Table(name = "agency_module")
public class AgencyModule {

    @EmbeddedId
    private AgencyModuleId id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected AgencyModule() {
        // pour JPA
    }

    public static AgencyModule enable(UUID agencyId, ModuleCode module, Instant now) {
        AgencyModule agencyModule = new AgencyModule();
        agencyModule.id = new AgencyModuleId(agencyId, module.name());
        agencyModule.reenable(now);
        return agencyModule;
    }

    public void reenable(Instant now) {
        enabled = true;
        enabledAt = now;
        disabledAt = null;
    }

    public void disable(Instant now) {
        enabled = false;
        disabledAt = now;
    }

    public UUID getAgencyId() {
        return id.getAgencyId();
    }

    public ModuleCode getModule() {
        return ModuleCode.valueOf(id.getModuleCode());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getEnabledAt() {
        return enabledAt;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }
}
