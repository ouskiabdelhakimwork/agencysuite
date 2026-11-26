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
 *
 * <p>Cycle de vie : INVITED (invitation envoyee par Keycloak) &rarr; ACTIVE a la premiere connexion
 * &rarr; DISABLED (desactive par l'agence) &rarr; reactive. INVITED et ACTIVE donnent acces a
 * l'application (Keycloak ne laisse se connecter un invite qu'apres verification de son e-mail et
 * choix de son mot de passe) ; DISABLED, jamais.
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

    /** Nouvel utilisateur invite par l'agence (ou par l'editeur pour le gerant). */
    public static UserAccount invite(String externalIdpId, String email, String fullName) {
        return new UserAccount(externalIdpId, email, fullName, UserStatus.INVITED);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isInvited() {
        return status == UserStatus.INVITED;
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    /**
     * Premiere connexion d'un invite : INVITED &rarr; ACTIVE.
     *
     * @return vrai si le statut a change
     */
    public boolean activateOnFirstLogin() {
        if (status != UserStatus.INVITED) {
            return false;
        }
        status = UserStatus.ACTIVE;
        return true;
    }

    /**
     * Desactivation par l'agence : plus aucun acces, donnees conservees.
     *
     * @return vrai si le statut a change
     */
    public boolean disable() {
        if (status == UserStatus.DISABLED) {
            return false;
        }
        status = UserStatus.DISABLED;
        return true;
    }

    /**
     * Reactivation par l'agence : ACTIVE s'il s'est deja connecte, sinon INVITED (son invitation
     * reste a accepter). Sans effet si l'utilisateur n'est pas desactive.
     *
     * @return vrai si le statut a change
     */
    public boolean enable() {
        if (status != UserStatus.DISABLED) {
            return false;
        }
        status = lastLoginAt == null ? UserStatus.INVITED : UserStatus.ACTIVE;
        return true;
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
