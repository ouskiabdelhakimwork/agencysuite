package ma.agencysuite.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import ma.agencysuite.shared.persistence.AgencyScopedEntity;

/**
 * Utilisateur d'une agence. L'authentification est faite par Keycloak ; cette ligne dit si
 * l'utilisateur est actif et quels roles il a (ADR-08). Il est retrouve par le {@code sub} du
 * jeton ({@code external_idp_id}), unique sur toute la plateforme.
 */
@Entity
@Table(name = "user_account")
public class UserAccount extends AgencyScopedEntity {

    @Column(name = "external_idp_id", nullable = false, length = 36)
    private String externalIdpId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserAccount() {
        // pour JPA
    }

    public UserAccount(String externalIdpId, String email, String fullName, UserStatus status) {
        this.externalIdpId = Objects.requireNonNull(externalIdpId, "externalIdpId");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = fullName;
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    /** Connexion reussie (signalee par l'interface apres le retour du fournisseur d'identite). */
    public void recordLogin(Instant at) {
        lastLoginAt = Objects.requireNonNull(at, "at");
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

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
