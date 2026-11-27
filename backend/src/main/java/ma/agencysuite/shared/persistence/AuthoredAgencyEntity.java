package ma.agencysuite.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.UUID;
import ma.agencysuite.shared.security.ActorContext;

/**
 * Entite metier d'agence qui garde l'auteur de sa creation et de sa derniere modification
 * ({@code created_by}, {@code updated_by}), lu dans {@link ActorContext} : identifiant de la ligne
 * {@code user_account} ou {@code platform_user}, {@code NULL} pour une action du systeme.
 *
 * <p>Pas de cle etrangere sur ces colonnes : l'auteur peut etre l'un ou l'autre type d'utilisateur.
 * Socle des tables metier creees a partir de l'etape 5 ; les tables existantes ne changent pas.
 * {@code updated_by} n'est modifie que si Hibernate ecrit la ligne (entite modifiee).
 */
@MappedSuperclass
public abstract class AuthoredAgencyEntity extends AgencyScopedEntity {

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    void recordCreator() {
        UUID actor = ActorContext.current().id();
        createdBy = actor;
        updatedBy = actor;
    }

    @PreUpdate
    void recordUpdater() {
        updatedBy = ActorContext.current().id();
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
