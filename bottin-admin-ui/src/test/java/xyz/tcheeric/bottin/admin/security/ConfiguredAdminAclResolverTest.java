package xyz.tcheeric.bottin.admin.security;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.core.AclDecision;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConfiguredAdminAclResolver.
 *
 * <p>This is the single point that decides who administers the deployment, so
 * the tests cover all four outcomes rather than only the happy path: the
 * dangerous failure is not "the administrator cannot sign in", it is "somebody
 * else can".
 */
class ConfiguredAdminAclResolverTest {

    /** A known keypair: the npub and hex below are the same key. */
    private static final String ADMIN_NPUB =
            "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String ADMIN_HEX =
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    private static final String OTHER_HEX =
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    /**
     * Tests that the configured administrator is admitted as the super
     * administrator, which is the role the whole dashboard is gated on.
     */
    @Test
    void shouldAdmitTheConfiguredKeyAsSuperAdmin() {
        // Given: a deployment configured with an administrator npub
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX);

        // Then: it is admitted, holding the super administrator role
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.roles()).containsExactly(AdminPermissions.SUPER_ADMIN);
    }

    /**
     * Tests that the super administrator holds every permission, including the
     * one reserved for managing other administrators.
     */
    @Test
    void shouldGrantTheSuperAdminEveryPermission() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX);

        // Then: read, write, and managing administrators are all granted
        assertThat(decision.permissions()).containsExactlyInAnyOrder(
                AdminPermissions.READ, AdminPermissions.WRITE, AdminPermissions.MANAGE_ADMINS);
    }

    /**
     * Tests that the same key configured in hex rather than npub is recognised,
     * so an operator pasting either form gets one administrator, not none.
     */
    @Test
    void shouldAdmitTheConfiguredKeyWhenGivenAsHex() {
        // Given: the administrator key configured in hex form
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_HEX);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX);

        // Then: it is admitted just as the npub form would be
        assertThat(decision.allowed()).isTrue();
    }

    /**
     * Tests that a key which is not the configured one is refused. This is the
     * outcome that makes replacing the password safe rather than merely
     * different.
     */
    @Test
    void shouldRefuseAKeyThatIsNotTheConfiguredOne() {
        // Given: a deployment configured with one administrator
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When: a different key proves itself
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, OTHER_HEX);

        // Then: it is refused, with no role and no permission
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.roles()).isEmpty();
        assertThat(decision.permissions()).isEmpty();
    }

    /**
     * Tests that an unconfigured deployment admits nobody rather than everybody,
     * which is the failure direction that matters on a fresh install.
     */
    @Test
    void shouldRefuseEverybodyWhenNoKeyIsConfigured() {
        // Given: no administrator key configured
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(null);

        // When: any key proves itself
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX);

        // Then: it is refused
        assertThat(decision.allowed()).isFalse();
    }

    /**
     * Tests that a blank configured value counts as unconfigured, since an
     * environment variable set to the empty string is how "unset" usually
     * reaches a container.
     */
    @Test
    void shouldTreatABlankConfiguredValueAsUnconfigured() {
        // Given: an administrator key configured as blank
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver("   ");

        // When & Then: nobody is admitted, and the state says why
        assertThat(resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX).allowed()).isFalse();
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.NOT_CONFIGURED);
    }

    /**
     * Tests that a configured value which is not a key at all is reported as a
     * misconfiguration, distinctly from a key that simply does not match.
     */
    @Test
    void shouldReportAnUnusableConfiguredValueAsMisconfiguration() {
        // Given: a configured value that is not a public key
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver("not-a-key");

        // When & Then: nobody is admitted, and the state distinguishes this from
        // "not configured" so an operator is not sent looking for a missing value
        assertThat(resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX).allowed()).isFalse();
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.UNREADABLE);
    }

    /**
     * Tests that a correctly configured deployment reports itself as such, which
     * is what lets the sign-in page offer a form that can actually succeed.
     */
    @Test
    void shouldReportConfiguredWhenTheKeyIsUsable() {
        // Given: a usable administrator key
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When & Then
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.CONFIGURED);
    }

    /**
     * Tests that a request scoped to another application is refused, so an ACL
     * record issued for the client cannot be honoured here.
     */
    @Test
    void shouldRefuseWhenTheRequestIsForAnotherApplication() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When: the administrator's key is offered for a different application
        AclDecision decision = resolver.resolve("some-other-app", ADMIN_HEX);

        // Then: it is refused
        assertThat(decision.allowed()).isFalse();
    }

    /**
     * Tests that a proven key in upper case is still recognised, since hex
     * casing is not meaningful and a mismatch here would be baffling.
     */
    @Test
    void shouldIgnoreCaseWhenComparingTheProvenKey() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = new ConfiguredAdminAclResolver(ADMIN_NPUB);

        // When: the same key is proven in upper case
        AclDecision decision = resolver.resolve(AdminPermissions.APP_ID, ADMIN_HEX.toUpperCase());

        // Then: it is admitted
        assertThat(decision.allowed()).isTrue();
    }
}
