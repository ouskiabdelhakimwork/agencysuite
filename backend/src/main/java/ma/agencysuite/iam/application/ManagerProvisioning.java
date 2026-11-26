package ma.agencysuite.iam.application;

import java.util.Set;
import java.util.UUID;
import ma.agencysuite.iam.application.UserInvitations.InvitationResult;
import ma.agencysuite.iam.application.UserInvitations.InviteUserCommand;
import ma.agencysuite.iam.domain.SystemRole;
import ma.agencysuite.platform.AgencyUserSettings;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Rattachement du gerant d'une agence par l'editeur (fin de US-101) : Organization Keycloak de
 * l'agence creee si besoin (idempotent, audit ORGANIZATION_PROVISIONED a la premiere creation), puis
 * invitation MANAGER selon les regles de {@link UserInvitations}.
 *
 * <p>Appele uniquement depuis le back-office, dans le perimetre plateforme.
 */
@Service
public class ManagerProvisioning {

    private final PlatformApi platform;
    private final UserInvitations invitations;

    ManagerProvisioning(PlatformApi platform, UserInvitations invitations) {
        this.platform = platform;
        this.invitations = invitations;
    }

    /** Coordonnees du gerant a inviter. */
    public record ManagerCommand(String email, String firstName, String lastName) {
    }

    /**
     * @throws UnknownAgencyException agence inconnue
     * @throws AgencyClosedException agence close
     */
    public InvitationResult provisionManager(UUID agencyUuid, ManagerCommand command) {
        TenantContext.requirePlatform();
        AgencyId agencyId = new AgencyId(agencyUuid);
        AgencyUserSettings settings = platform.userSettings(agencyId)
                .orElseThrow(() -> new UnknownAgencyException(agencyUuid));
        if (settings.closed()) {
            throw new AgencyClosedException(agencyUuid);
        }
        InviteUserCommand invite = new InviteUserCommand(command.email(), command.firstName(), command.lastName(),
                Set.of(SystemRole.MANAGER));
        // Exception a la regle "un service n'ouvre jamais de perimetre" (CLAUDE.md) : le rattachement du
        // gerant par le back-office est le seul point d'entree autorise a ouvrir le perimetre d'une agence,
        // celle ciblee par l'editeur, pour cette seule operation. Les lectures et ecritures (user_account,
        // user_role, audit_log) passent ainsi par le filtre @TenantId et la RLS de cette agence, et non par
        // le perimetre plateforme ; l'auteur reste le membre de l'editeur (ActorContext).
        return TenantContext.callAsAgency(agencyId, () -> invitations.invite(invite));
    }
}
