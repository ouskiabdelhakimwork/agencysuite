package ma.agencysuite.iam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import ma.agencysuite.TestTokens;
import ma.agencysuite.TestcontainersConfiguration;
import ma.agencysuite.iam.IamTestData;
import ma.agencysuite.iam.domain.PlatformUserStatus;
import ma.agencysuite.iam.domain.UserStatus;
import ma.agencysuite.platform.PlatformTestData;
import ma.agencysuite.shared.security.CurrentUser;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.tenant.AgencyId;
import ma.agencysuite.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Resolution de l'utilisateur courant a partir du sub, dans le perimetre de l'appelant. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CurrentUserResolverTest {

    @Autowired
    private CurrentUserResolver resolver;
    @Autowired
    private PlatformTestData data;
    @Autowired
    private IamTestData iam;

    private final String suffix = PlatformTestData.randomSuffix();
    private UUID agencyA;
    private UUID agencyB;

    @BeforeEach
    void createTwoAgencies() {
        agencyA = data.createAgency("resolver-a-" + suffix);
        agencyB = data.createAgency("resolver-b-" + suffix);
    }

    @AfterEach
    void deleteTestAgencies() {
        data.deleteAgenciesWithSuffix(suffix);
    }

    @Test
    void activeUserGetsTheUnionOfThePermissionsOfItsRoles() {
        String sub = iam.createUser(agencyA, UserStatus.ACTIVE, "SALES", "ACCOUNTANT");

        Optional<CurrentUser> user = inAgency(agencyA, sub);

        assertThat(user).isPresent();
        assertThat(user.get().permissions()).containsExactlyInAnyOrder(
                PermissionCode.IDENTITY_READ, PermissionCode.TICKETING_WRITE, PermissionCode.BILLING_ISSUE);
    }

    @Test
    void managerGetsEveryPermission() {
        Optional<CurrentUser> manager = inAgency(agencyA, TestTokens.managerSub(agencyA));

        assertThat(manager).isPresent();
        assertThat(manager.get().permissions()).containsExactlyInAnyOrder(PermissionCode.values());
    }

    @Test
    void activeUserWithoutRoleIsFoundWithoutPermission() {
        String sub = iam.createUser(agencyA, UserStatus.ACTIVE);

        assertThat(inAgency(agencyA, sub)).hasValueSatisfying(user -> assertThat(user.permissions()).isEmpty());
    }

    @Test
    void invitedOrDisabledUserIsNotResolved() {
        String invited = iam.createUser(agencyA, UserStatus.INVITED, "MANAGER");
        String disabled = iam.createUser(agencyA, UserStatus.ACTIVE, "MANAGER");
        iam.disableUser(agencyA, disabled);

        assertThat(inAgency(agencyA, invited)).isEmpty();
        assertThat(inAgency(agencyA, disabled)).isEmpty();
    }

    @Test
    void userOfAgencyBIsNotFoundFromTheScopeOfAEvenWithItsSub() {
        String subOfB = iam.createUser(agencyB, UserStatus.ACTIVE, "MANAGER");

        assertThat(inAgency(agencyA, subOfB)).isEmpty();
        assertThat(inAgency(agencyB, subOfB)).isPresent();
    }

    @Test
    void agencyUserIsNotResolvedOutsideAnAgencyScope() {
        String sub = TestTokens.managerSub(agencyA);

        assertThat(resolver.resolveAgencyUser(sub)).isEmpty();
        assertThat(TenantContext.callAsPlatform(() -> resolver.resolveAgencyUser(sub))).isEmpty();
        assertThat(inAgency(agencyA, null)).isEmpty();
    }

    @Test
    void platformUserMustBeActiveAndIsOnlyCheckedInThePlatformScope() {
        String disabled = iam.createPlatformUser(PlatformUserStatus.DISABLED);
        try {
            assertThat(TenantContext.callAsPlatform(
                    () -> resolver.isActivePlatformUser(TestTokens.PLATFORM_ADMIN_SUB))).isTrue();
            assertThat(TenantContext.callAsPlatform(() -> resolver.isActivePlatformUser(disabled))).isFalse();
            assertThat(TenantContext.callAsPlatform(
                    () -> resolver.isActivePlatformUser(UUID.randomUUID().toString()))).isFalse();
            assertThat(TenantContext.callAsAgency(new AgencyId(agencyA),
                    () -> resolver.isActivePlatformUser(TestTokens.PLATFORM_ADMIN_SUB))).isFalse();
            assertThat(resolver.isActivePlatformUser(TestTokens.PLATFORM_ADMIN_SUB)).isFalse();
        } finally {
            iam.deletePlatformUser(disabled);
        }
    }

    private Optional<CurrentUser> inAgency(UUID agencyId, String sub) {
        return TenantContext.callAsAgency(new AgencyId(agencyId), () -> resolver.resolveAgencyUser(sub));
    }
}
