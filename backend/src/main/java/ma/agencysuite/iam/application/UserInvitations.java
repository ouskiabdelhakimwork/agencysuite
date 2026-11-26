package ma.agencysuite.iam.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import ma.agencysuite.iam.domain.AuditAction;
import ma.agencysuite.iam.domain.SystemRole;
import ma.agencysuite.iam.domain.UserAccount;
import ma.agencysuite.iam.domain.UserRole;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.iam.infrastructure.UserAccountRepository;
import ma.agencysuite.iam.infrastructure.UserRoleRepository;
import ma.agencysuite.platform.AgencyUserSettings;
import ma.agencysuite.platform.PlatformApi;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invitation des utilisateurs d'une agence (US-201) : compte Keycloak, membre de l'Organization de
 * l'agence, ligne user_account INVITED et ses roles, puis e-mail d'invitation envoye par Keycloak
 * (l'utilisateur verifie son adresse et choisit lui-meme son mot de passe).
 *
 * <p>Keycloak et la base ne sont pas dans la meme transaction, d'ou l'ordre :
 * <ol>
 *   <li>controles en base (e-mail libre dans l'agence, quota du palier) ;</li>
 *   <li>Keycloak : Organization (idempotent), utilisateur, membre ;</li>
 *   <li>base, une transaction : utilisateur INVITED, roles, audit USER_INVITED ;</li>
 *   <li>si 2 (apres la creation de l'utilisateur) ou 3 echoue : compensation, l'utilisateur
 *       Keycloak est supprime, puis l'erreur d'origine est relancee ;</li>
 *   <li>apres le commit : envoi de l'invitation. Un echec n'annule rien
 *       ({@code invitationSent = false}, le gerant pourra la renvoyer).</li>
 * </ol>
 *
 * <p>N'ouvre jamais de perimetre : travaille dans l'agence courante (seul le rattachement du gerant
 * par le back-office, {@link ManagerProvisioning}, ouvre celui de l'agence ciblee). Jamais d'e-mail
 * ni de nom dans les journaux ou le journal d'audit : seul le {@code sub} (pseudonyme) est journalise.
 */
@Service
public class UserInvitations {

    static final String USER = "USER";
    static final String ORGANIZATION = "ORGANIZATION";

    private static final Logger LOG = LoggerFactory.getLogger(UserInvitations.class);

    private final UserAccountRepository users;
    private final UserRoleRepository roles;
    private final PlatformApi platform;
    private final IdentityProviderAdmin identityProvider;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;

    UserInvitations(UserAccountRepository users, UserRoleRepository roles, PlatformApi platform,
            IdentityProviderAdmin identityProvider, AuditTrail audit, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.roles = roles;
        this.platform = platform;
        this.identityProvider = identityProvider;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** Demande d'invitation. L'e-mail est normalise (espaces retires, minuscules). */
    public record InviteUserCommand(String email, String firstName, String lastName, Set<SystemRole> roles) {

        public InviteUserCommand {
            email = Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
            firstName = Objects.requireNonNull(firstName, "firstName").strip();
            lastName = Objects.requireNonNull(lastName, "lastName").strip();
            roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Au moins un role est requis");
            }
        }

        String fullName() {
            return firstName + " " + lastName;
        }
    }

    /** Utilisateur invite ; {@code invitationSent} faux si l'e-mail n'a pas pu etre envoye. */
    public record InvitationResult(UUID userId, UserStatus status, boolean invitationSent) {
    }

    /**
     * Invite un utilisateur dans l'agence courante.
     *
     * @throws EmailAlreadyRegisteredException e-mail deja utilise dans l'agence ou chez le fournisseur
     * @throws UserQuotaExceededException palier atteint (ou aucun abonnement actif)
     * @throws AgencyClosedException agence close
     * @throws IdentityAdminUnavailableException fournisseur d'identite injoignable ou non configure
     */
    public InvitationResult invite(InviteUserCommand command) {
        AgencyId agencyId = TenantContext.requireAgency();
        AgencyUserSettings settings = platform.userSettings(agencyId)
                .orElseThrow(() -> new IllegalStateException("Agence courante illisible"));
        if (settings.closed()) {
            throw new AgencyClosedException(agencyId.value());
        }
        readOnly.executeWithoutResult(status -> {
            if (users.existsByEmailIgnoreCase(command.email())) {
                throw new EmailAlreadyRegisteredException();
            }
            if (!settings.allowsAnotherUser(users.countByStatusNot(UserStatus.DISABLED))) {
                throw new UserQuotaExceededException();
            }
        });

        ProvisionedOrganization organization =
                identityProvider.ensureOrganization(agencyId, organizationName(settings));
        if (organization.created()) {
            transaction.executeWithoutResult(status -> audit.record(agencyId, AuditAction.ORGANIZATION_PROVISIONED,
                    ORGANIZATION, organization.id(), Map.of()));
        }
        String sub = identityProvider.createUser(command.email(), command.firstName(), command.lastName(),
                settings.defaultLocale());
        UUID userId;
        try {
            identityProvider.addMember(organization.id(), sub);
            userId = transaction.execute(status -> save(agencyId, sub, command));
        } catch (RuntimeException e) {
            compensate(sub);
            throw e;
        }
        return new InvitationResult(userId, UserStatus.INVITED, trySendInvitation(sub));
    }

    /**
     * Renvoie l'e-mail d'invitation d'un utilisateur INVITED de l'agence courante. Un echec d'envoi
     * annule l'entree d'audit (503).
     *
     * @throws UserNotFoundException utilisateur inconnu dans l'agence
     * @throws UserNotInvitedException utilisateur deja actif ou desactive
     */
    public void resendInvitation(UUID userId) {
        AgencyId agencyId = TenantContext.requireAgency();
        transaction.executeWithoutResult(status -> {
            UserAccount user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
            if (!user.isInvited()) {
                throw new UserNotInvitedException();
            }
            identityProvider.sendInvitation(user.getExternalIdpId());
            audit.record(agencyId, AuditAction.INVITATION_RESENT, USER, userId.toString(), Map.of());
        });
    }

    private UUID save(AgencyId agencyId, String sub, InviteUserCommand command) {
        UserAccount user = users.save(UserAccount.invite(sub, command.email(), command.fullName()));
        List<String> codes = command.roles().stream().map(SystemRole::name).sorted().toList();
        for (String code : codes) {
            roles.save(new UserRole(user.getId(), code));
        }
        users.flush();
        audit.record(agencyId, AuditAction.USER_INVITED, USER, user.getId().toString(), Map.of("roles", codes));
        return user.getId();
    }

    private boolean trySendInvitation(String sub) {
        try {
            identityProvider.sendInvitation(sub);
            return true;
        } catch (IdentityAdminUnavailableException e) {
            LOG.warn("Invitation non envoyee pour sub={} ({}) : a renvoyer", sub, e.getMessage());
            return false;
        }
    }

    private void compensate(String sub) {
        try {
            identityProvider.deleteUser(sub);
        } catch (RuntimeException e) {
            LOG.warn("Compensation impossible : utilisateur sub={} a supprimer chez le fournisseur d'identite ({})",
                    sub, e.getClass().getSimpleName());
        }
    }

    /** Nom de l'Organization : unique chez Keycloak, d'ou le sous-domaine (deux agences peuvent etre homonymes). */
    static String organizationName(AgencyUserSettings settings) {
        return settings.name() + " (" + settings.subdomain() + ")";
    }
}
