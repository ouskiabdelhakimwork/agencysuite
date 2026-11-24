package ma.agencysuite.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import org.hibernate.annotations.Immutable;

/**
 * Permission accordee a un role systeme (donnee de reference partagee par toutes les agences,
 * en lecture seule pour l'application ; modifiee uniquement par migration).
 */
@Entity
@Immutable
@Table(name = "system_role_permission")
public class SystemRolePermission {

    @EmbeddedId
    private Id id;

    protected SystemRolePermission() {
        // pour JPA
    }

    public String getRoleCode() {
        return id.roleCode;
    }

    public String getPermissionCode() {
        return id.permissionCode;
    }

    /** Cle composee (role_code, permission_code). */
    @Embeddable
    public record Id(
            @Column(name = "role_code", length = 30) String roleCode,
            @Column(name = "permission_code", length = 50) String permissionCode) implements Serializable {
    }
}
