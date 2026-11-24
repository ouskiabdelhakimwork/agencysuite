package ma.agencysuite.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.persistence.AgencyScopedEntity;

/**
 * Role systeme attribue a un utilisateur de l'agence. Entite a part entiere (et non collection
 * d'elements de {@link UserAccount}) pour que Hibernate remplisse agency_id.
 */
@Entity
@Table(name = "user_role")
public class UserRole extends AgencyScopedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role_code", nullable = false, updatable = false, length = 30)
    private String roleCode;

    protected UserRole() {
        // pour JPA
    }

    public UserRole(UUID userId, String roleCode) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode");
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRoleCode() {
        return roleCode;
    }
}
