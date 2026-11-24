package ma.agencysuite.shared.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ma.agencysuite.shared.persistence.AgencyScopedEntity;

/** Entite sonde (tests uniquement) : la plus petite donnee metier appartenant a une agence. */
@Entity
@Table(name = "tenant_probe")
public class TenantProbe extends AgencyScopedEntity {

    @Column(nullable = false, length = 100)
    private String label;

    protected TenantProbe() {
        // pour JPA
    }

    public TenantProbe(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
