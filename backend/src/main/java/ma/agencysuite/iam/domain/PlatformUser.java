package ma.agencysuite.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.persistence.BaseEntity;

/**
 * Membre de l'equipe de l'editeur (realm Keycloak "platform"). N'appartient a aucune agence :
 * table a part, lisible uniquement dans le perimetre plateforme (politique RLS platform_only).
 */
@Entity
@Table(name = "platform_user")
public class PlatformUser extends BaseEntity {

    @Column(name = "external_idp_id", nullable = false, length = 36)
    private String externalIdpId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformUserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected PlatformUser() {
        // pour JPA
    }

    public PlatformUser(String externalIdpId, String email, String fullName, PlatformRole role,
            PlatformUserStatus status) {
        this.externalIdpId = Objects.requireNonNull(externalIdpId, "externalIdpId");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = fullName;
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == PlatformUserStatus.ACTIVE;
    }

    public void disable() {
        status = PlatformUserStatus.DISABLED;
    }

    public String getExternalIdpId() {
        return externalIdpId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public PlatformRole getRole() {
        return role;
    }

    public PlatformUserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
