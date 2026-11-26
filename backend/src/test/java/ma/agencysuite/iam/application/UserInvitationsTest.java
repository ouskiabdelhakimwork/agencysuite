package ma.agencysuite.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.AuditTestData;
import ma.agencysuite.iam.AuditTestData.Row;
import ma.agencysuite.iam.FakeIdentityProviderAdmin;
import ma.agencysuite.iam.FakeIdentityProviderAdmin.Operation;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.application.UserInvitations.InvitationResult;
import ma.agencysuite.iam.application.UserInvitations.InviteUserCommand;
import ma.agencysuite.iam.domain.SystemRole;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Invitation d'un utilisateur : ordre base / Keycloak, compensation, envoi apres le commit, quota.
 * Keycloak est remplace par {@link FakeIdentityProviderAdmin} (pannes simulees).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserInvitationsTest {

    @Autowired
    private UserInvitations invitations;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;
    @Autowired
    private AuditTestData audit;
    @Autowired
    private FakeIdentityProviderAdmin identityProvider;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyId;

    @BeforeEach
    void createAgency() {
        agencyId = data.createAgency("invite-" + suffix);
        data.subscribe(agencyId, "STARTER"); // 3 utilisateurs, dont le gerant cree avec l'agence
    }

    @AfterEach
    void cleanUp() {
        identityProvider.reset();
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void invitesTheUserInKeycloakThenInTheDatabaseAndSendsTheInvitationAfterTheCommit() {
        String email = email("sales");
        AtomicReference<Long> usersWhenSent = new AtomicReference<>();
        identityProvider.onSendInvitation(sub -> usersWhenSent.set(iam.countUsers(agencyId)));

        InvitationResult result = invite(new InviteUserCommand(" " + email.toUpperCase() + " ", "Nouvel", "Vendeur",
                Set.of(SystemRole.SALES, SystemRole.OPERATIONS)));

        assertThat(result.status()).isEqualTo(UserStatus.INVITED);
        assertThat(result.invitationSent()).isTrue();
        assertThat(iam.status(agencyId, result.userId())).isEqualTo(UserStatus.INVITED);
        assertThat(iam.roles(agencyId, result.userId())).containsExactly("OPERATIONS", "SALES");
        String sub = iam.user(agencyId, result.userId()).orElseThrow().getExternalIdpId();
        assertThat(iam.user(agencyId, result.userId()).orElseThrow().getEmail()).isEqualTo(email);
        assertThat(identityProvider.user(sub)).hasValueSatisfying(user -> {
            assertThat(user.email()).isEqualTo(email);
            assertThat(user.locale()).isEqualTo("fr");
        });
        String organization = identityProvider.organizationOf(agencyId).orElseThrow();
        assertThat(identityProvider.organizationName(organization)).hasValue("Agence invite-" + suffix + " (invite-" + suffix + ")");
        assertThat(identityProvider.membersOf(organization)).contains(sub);
        assertThat(identityProvider.calls()).extracting(FakeIdentityProviderAdmin.Call::operation).containsExactly(
                Operation.ENSURE_ORGANIZATION, Operation.CREATE_USER, Operation.ADD_MEMBER, Operation.SEND_INVITATION);
        // L'invitation part une fois l'utilisateur enregistre (visible d'une autre transaction) : gerant + invite.
        assertThat(usersWhenSent.get()).isEqualTo(2L);

        List<Row> entries = audit.entries(agencyId);
        assertThat(entries).extracting(Row::action).containsExactly("ORGANIZATION_PROVISIONED", "USER_INVITED");
        Row invited = entries.getLast();
        assertThat(invited.entityType()).isEqualTo("USER");
        assertThat(invited.entityId()).isEqualTo(result.userId().toString());
        assertThat(invited.details()).isEqualTo("{\"roles\": [\"OPERATIONS\", \"SALES\"]}");
        assertThat(invited.actorType()).isEqualTo("SYSTEM");
    }

    @Test
    void theOrganizationIsCreatedOnlyOnce() {
        invite(command(email("one"), SystemRole.SALES));
        invite(command(email("two"), SystemRole.SALES));

        assertThat(audit.entries(agencyId)).extracting(Row::action)
                .containsExactly("ORGANIZATION_PROVISIONED", "USER_INVITED", "USER_INVITED");
    }

    @Test
    void anEmailAlreadyUsedInTheAgencyIsRefusedWithoutCallingKeycloak() {
        invite(command(email("twice"), SystemRole.SALES));
        identityProvider.reset();

        assertThatThrownBy(() -> invite(command(email("twice").toUpperCase(), SystemRole.OPERATIONS)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void anEmailAlreadyKnownByKeycloakIsRefusedAndNothingIsSaved() {
        identityProvider.preexistingUser(email("elsewhere"));

        assertThatThrownBy(() -> invite(command(email("elsewhere"), SystemRole.SALES)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(iam.countUsers(agencyId)).isEqualTo(1); // le gerant seul
    }

    @Test
    void theQuotaCountsInvitedAndActiveUsersButNotDisabledOnes() {
        invite(command(email("u2"), SystemRole.SALES));
        UUID third = invite(command(email("u3"), SystemRole.SALES)).userId();

        assertThatThrownBy(() -> invite(command(email("u4"), SystemRole.SALES)))
                .isInstanceOf(UserQuotaExceededException.class);

        iam.disableUser(agencyId, iam.user(agencyId, third).orElseThrow().getExternalIdpId());
        assertThat(invite(command(email("u4"), SystemRole.SALES)).status()).isEqualTo(UserStatus.INVITED);
    }

    @Test
    void anAgencyWithoutActiveSubscriptionCannotInvite() {
        UUID unsubscribed = data.createAgency("nosub-" + suffix);

        assertThatThrownBy(() -> TenantContext.callAsAgency(new AgencyId(unsubscribed),
                () -> invitations.invite(command(email("nosub"), SystemRole.SALES))))
                .isInstanceOf(UserQuotaExceededException.class);
    }

    @Test
    void aClosedAgencyCannotInvite() {
        data.close(agencyId);

        assertThatThrownBy(() -> invite(command(email("closed"), SystemRole.SALES)))
                .isInstanceOf(AgencyClosedException.class);
        assertThat(identityProvider.calls()).isEmpty();
    }

    @Test
    void aDatabaseFailureAfterTheKeycloakCreationDeletesTheKeycloakUser() {
        // Keycloak renvoie le sub du gerant deja en base : la contrainte d'unicite de
        // external_idp_id fait echouer l'insertion, apres la creation dans Keycloak.
        String sub = TestTokens.managerSub(agencyId);
        identityProvider.returnSubOnNextCreate(sub);

        assertThatThrownBy(() -> invite(command(email("dbfail"), SystemRole.SALES)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(identityProvider.calls(Operation.ADD_MEMBER)).extracting(FakeIdentityProviderAdmin.Call::target)
                .containsExactly(sub);
        assertThat(identityProvider.calls(Operation.DELETE_USER)).extracting(FakeIdentityProviderAdmin.Call::target)
                .containsExactly(sub);
        assertThat(identityProvider.user(sub)).isEmpty();
        assertThat(identityProvider.calls(Operation.SEND_INVITATION)).isEmpty();
        assertThat(iam.countUsers(agencyId)).isEqualTo(1);
        assertThat(audit.entries(agencyId)).extracting(Row::action).doesNotContain("USER_INVITED");
    }

    @Test
    void aMembershipFailureAlsoDeletesTheKeycloakUser() {
        identityProvider.failOn(Operation.ADD_MEMBER);

        assertThatThrownBy(() -> invite(command(email("nomember"), SystemRole.SALES)))
                .isInstanceOf(IdentityAdminUnavailableException.class);

        assertThat(identityProvider.calls(Operation.DELETE_USER)).hasSize(1);
        assertThat(identityProvider.userByEmail(email("nomember"))).isEmpty();
        assertThat(iam.countUsers(agencyId)).isEqualTo(1);
    }

    @Test
    void aFailedInvitationEmailKeepsTheUserAndSaysSo() {
        identityProvider.failOn(Operation.SEND_INVITATION);

        InvitationResult result = invite(command(email("nomail"), SystemRole.SALES));

        assertThat(result.invitationSent()).isFalse();
        assertThat(iam.status(agencyId, result.userId())).isEqualTo(UserStatus.INVITED);
        assertThat(identityProvider.calls(Operation.DELETE_USER)).isEmpty();
    }

    @Test
    void keycloakUnavailableBeforeTheUserCreationSavesNothing() {
        identityProvider.failOn(Operation.ENSURE_ORGANIZATION);

        assertThatThrownBy(() -> invite(command(email("down"), SystemRole.SALES)))
                .isInstanceOf(IdentityAdminUnavailableException.class);
        assertThat(identityProvider.calls(Operation.CREATE_USER)).isEmpty();
        assertThat(iam.countUsers(agencyId)).isEqualTo(1);
    }

    private InvitationResult invite(InviteUserCommand command) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> invitations.invite(command));
    }

    private static InviteUserCommand command(String email, SystemRole role) {
        return new InviteUserCommand(email, "Prenom", "Nom", Set.of(role));
    }

    private String email(String name) {
        return name.toLowerCase() + "-" + suffix + "@example.test";
    }
}
