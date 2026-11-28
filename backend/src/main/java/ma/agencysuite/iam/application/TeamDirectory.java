package ma.agencysuite.iam.application;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Annuaire de l'equipe de l'agence courante (etape 5, lot 4), en lecture pour tous ses
 * utilisateurs : sert a afficher le nom du responsable d'un client ou d'une demande (le frontend
 * fait la correspondance identifiant &rarr; nom ; crm ne depend pas de iam).
 *
 * <p>Seulement les utilisateurs ACTIVE et INVITED, et seulement leur identifiant, leur nom et leur
 * statut : ni e-mail, ni roles, ni date de connexion (la liste complete reste reservee a
 * {@code user.manage}). N'ouvre aucun perimetre.
 */
@Service
public class TeamDirectory {

    private static final EnumSet<UserStatus> LISTED = EnumSet.of(UserStatus.ACTIVE, UserStatus.INVITED);

    /** Membre de l'equipe ; {@code fullName} peut etre nul (compte cree sans nom). */
    public record TeamMember(UUID id, String fullName, UserStatus status) {
    }

    private final UserAccountRepository users;

    TeamDirectory(UserAccountRepository users) {
        this.users = users;
    }

    /** Membres actifs et invites de l'agence courante, par nom. */
    @Transactional(readOnly = true)
    public List<TeamMember> list() {
        TenantContext.requireAgency();
        return users.findByStatusInOrderByName(LISTED).stream()
                .map(user -> new TeamMember(user.getId(), user.getFullName(), user.getStatus()))
                .toList();
    }
}
