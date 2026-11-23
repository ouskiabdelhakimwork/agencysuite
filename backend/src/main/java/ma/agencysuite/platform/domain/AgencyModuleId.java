package ma.agencysuite.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Cle composee de agency_module : (agence, code du module). */
@Embeddable
public class AgencyModuleId implements Serializable {

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "module_code", nullable = false, length = 30)
    private String moduleCode;

    protected AgencyModuleId() {
        // pour JPA
    }

    public AgencyModuleId(UUID agencyId, String moduleCode) {
        this.agencyId = Objects.requireNonNull(agencyId, "agencyId");
        this.moduleCode = Objects.requireNonNull(moduleCode, "moduleCode");
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgencyModuleId that)) {
            return false;
        }
        return agencyId.equals(that.agencyId) && moduleCode.equals(that.moduleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agencyId, moduleCode);
    }
}
