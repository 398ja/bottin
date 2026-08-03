package xyz.tcheeric.bottin.admin.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Collectors;
import xyz.tcheeric.bottin.admin.config.AdminPermissionRegistryConfig;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.server.acl.PermissionRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /** A second real key, for "somebody else" in bech32 form. */
    private static final String OTHER_NPUB =
            "npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn";

    /**
     * The stored administrator list. Answers {@code Optional.empty()} for every
     * key unless a test says otherwise, so the configured-key cases stay
     * unaffected by it.
     */
    private final AdminUserService storedAdministrators = mock(AdminUserService.class);

    /**
     * The production registry, not a stand-in. A registry written here could
     * declare a mapping the deployment does not, and the tests below would then
     * agree with themselves while the application granted something else.
     */
    private static final PermissionRegistry REGISTRY =
            new AdminPermissionRegistryConfig().adminPermissionRegistry();

    private ConfiguredAdminAclResolver resolverFor(String configuredKey) {
        return new ConfiguredAdminAclResolver(configuredKey, storedAdministrators, REGISTRY);
    }

    /** Stores {@code pubkey} as an administrator holding {@code role}. */
    private void stored(String pubkey, AdminRole role) {
        when(storedAdministrators.roleOf(pubkey)).thenReturn(Optional.of(role));
    }

    /**
     * Tests that an administrator added to the stored list is admitted, which is
     * the whole point of the feature.
     */
    @Test
    void shouldAdmitAStoredAdministrator() {
        // Given: a key that is not the configured one but is a stored administrator
        stored(OTHER_HEX, AdminRole.ADMIN);

        // When: that key proves itself
        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        // Then: it is admitted as an ordinary administrator
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.roles()).containsExactly(AdminPermissions.ADMIN);
    }

    /**
     * Tests that a stored administrator is not granted the permission that
     * manages administrators. This is what makes the two roles differ where the
     * decision is made rather than only in the rendered page.
     */
    @Test
    void shouldNotGrantManageAdminsToAStoredAdministrator() {
        stored(OTHER_HEX, AdminRole.ADMIN);

        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        assertThat(decision.permissions())
                .contains(AdminPermissions.READ, AdminPermissions.WRITE)
                .doesNotContain(AdminPermissions.MANAGE_ADMINS);
    }

    /**
     * Tests that a key which is neither configured nor stored is still refused,
     * so consulting the list did not open a second way in.
     */
    @Test
    void shouldRefuseAKeyThatIsNeitherConfiguredNorStored() {
        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        assertThat(decision.allowed()).isFalse();
    }

    /**
     * Tests that the configured key wins over a stored entry for the same key.
     *
     * <p>The interface cannot produce that state, but changing the configured
     * key to one already added would. Configuration decides, so the holder stays
     * the super administrator and a stored row cannot demote them.
     */
    @Test
    void shouldPreferConfigurationWhenTheMasterKeyIsAlsoStored() {
        stored(ADMIN_HEX, AdminRole.ADMIN);

        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(ADMIN_NPUB, ADMIN_HEX);

        assertThat(decision.roles()).containsExactly(AdminPermissions.SUPER_ADMIN);
        assertThat(decision.permissions()).contains(AdminPermissions.MANAGE_ADMINS);
    }

    /**
     * Tests that added administrators still work when the configured master key
     * is unreadable. A misconfiguration should not also revoke everyone else.
     */
    @Test
    void shouldAdmitAStoredAdministratorWhenTheMasterKeyIsUnreadable() {
        stored(OTHER_HEX, AdminRole.ADMIN);

        AclDecision decision = resolverFor("not-a-key").resolve(OTHER_NPUB, OTHER_HEX);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.roles()).containsExactly(AdminPermissions.ADMIN);
    }

    /**
     * Tests that the configured administrator is admitted as the super
     * administrator, which is the role the whole dashboard is gated on.
     */
    @Test
    void shouldAdmitTheConfiguredKeyAsSuperAdmin() {
        // Given: a deployment configured with an administrator npub
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

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
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

        // Then: every declared permission is granted
        assertThat(decision.permissions()).containsExactlyInAnyOrder(
                AdminPermissions.READ, AdminPermissions.WRITE,
                AdminPermissions.SETTINGS_WRITE, AdminPermissions.MANAGE_ADMINS);
    }

    /**
     * Tests that an administrator stored as READONLY is admitted holding only
     * the read permission.
     *
     * <p>The column has accepted READONLY since V1 and nothing read it, so such
     * a row was granted write access. This is the assertion that the value now
     * decides something.
     */
    @Test
    void shouldGrantOnlyReadToAStoredReadonlyAdministrator() {
        // Given: a stored administrator recorded as read-only
        stored(OTHER_HEX, AdminRole.READONLY);

        // When: that key proves itself
        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        // Then: admitted, but holding nothing that changes anything
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.roles()).containsExactly(AdminPermissions.READONLY);
        assertThat(decision.permissions()).containsExactly(AdminPermissions.READ);
    }

    /**
     * Tests that the settings permission is withheld from a read-only
     * administrator, asserted separately because it is the permission this
     * change introduced and the one a role split exists to withhold.
     */
    @Test
    void shouldNotGrantSettingsWriteToAStoredReadonlyAdministrator() {
        stored(OTHER_HEX, AdminRole.READONLY);

        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        assertThat(decision.permissions())
                .doesNotContain(AdminPermissions.SETTINGS_WRITE, AdminPermissions.WRITE);
    }

    /**
     * Tests that an added administrator can write records but not settings.
     *
     * <p>Both halves in one test because the single assertion is the
     * distinction: holding {@code admin:write} while lacking
     * {@code admin:settings-write} is what the split means. Asserting only the
     * absence would pass equally for a role that could do nothing at all.
     */
    @Test
    void shouldGrantWriteButNotSettingsWriteToAStoredAdministrator() {
        stored(OTHER_HEX, AdminRole.ADMIN);

        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        assertThat(decision.permissions())
                .contains(AdminPermissions.WRITE)
                .doesNotContain(AdminPermissions.SETTINGS_WRITE);
    }

    /**
     * Tests that a disabled administrator is refused.
     *
     * <p>Asserted here rather than only on the service because the resolver is
     * what a disabled administrator would reach: the service returns no role for
     * them, and this confirms the resolver treats that as "not an administrator"
     * rather than as a role to look up.
     */
    @Test
    void shouldRefuseAnAdministratorTheStoreReportsNoRoleFor() {
        // Given: the store answers empty, as it does for a disabled entry
        // When
        AclDecision decision = resolverFor(ADMIN_NPUB).resolve(OTHER_NPUB, OTHER_HEX);

        // Then
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.permissions()).isEmpty();
    }

    /**
     * Tests that the same key configured in hex rather than npub is recognised,
     * so an operator pasting either form gets one administrator, not none.
     */
    @Test
    void shouldAdmitTheConfiguredKeyWhenGivenAsHex() {
        // Given: the administrator key configured in hex form
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_HEX);

        // When: that key proves itself
        AclDecision decision = resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

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
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: a different key proves itself
        AclDecision decision = resolver.resolve(OTHER_HEX, OTHER_HEX);

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
        ConfiguredAdminAclResolver resolver = resolverFor(null);

        // When: any key proves itself
        AclDecision decision = resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

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
        ConfiguredAdminAclResolver resolver = resolverFor("   ");

        // When & Then: nobody is admitted, and the state says why
        assertThat(resolver.resolve(ADMIN_NPUB, ADMIN_HEX).allowed()).isFalse();
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.NOT_CONFIGURED);
    }

    /**
     * Tests that a configured value which is not a key at all is reported as a
     * misconfiguration, distinctly from a key that simply does not match.
     */
    @Test
    void shouldReportAnUnusableConfiguredValueAsMisconfiguration() {
        // Given: a configured value that is not a public key
        ConfiguredAdminAclResolver resolver = resolverFor("not-a-key");

        // When & Then: nobody is admitted, and the state distinguishes this from
        // "not configured" so an operator is not sent looking for a missing value
        assertThat(resolver.resolve(ADMIN_NPUB, ADMIN_HEX).allowed()).isFalse();
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.UNREADABLE);
    }

    /**
     * Tests that a correctly configured deployment reports itself as such, which
     * is what lets the sign-in page offer a form that can actually succeed.
     */
    @Test
    void shouldReportConfiguredWhenTheKeyIsUsable() {
        // Given: a usable administrator key
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When & Then
        assertThat(resolver.keyState()).isEqualTo(AdminKeyState.CONFIGURED);
    }

    /**
     * Tests that the administrator is admitted when the identity arrives only in
     * bech32 form.
     *
     * <p>NAP passes the proven identity twice, as {@code (npub, pubkey)}. Reading
     * that pair as {@code (appId, pubkey)} is what refused every administrator in
     * 0.7.0 while the whole suite stayed green — the tests asserted the same
     * misreading as the code. This one is written from the library's own
     * parameter names.
     */
    @Test
    void shouldAdmitTheConfiguredKeyGivenOnlyInBech32Form() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: only the bech32 form of the identity is supplied
        AclDecision decision = resolver.resolve(ADMIN_NPUB, null);

        // Then: it is admitted
        assertThat(decision.allowed()).isTrue();
    }

    /**
     * Tests that a key that is not the configured one is still refused when it
     * arrives in bech32 form, so tolerating both encodings did not open a way in.
     */
    @Test
    void shouldRefuseAnotherKeyGivenOnlyInBech32Form() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: a different key proves itself in bech32 form
        AclDecision decision = resolver.resolve(OTHER_NPUB, null);

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
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When: the same key is proven in upper case
        AclDecision decision = resolver.resolve(ADMIN_NPUB, ADMIN_HEX.toUpperCase());

        // Then: it is admitted
        assertThat(decision.allowed()).isTrue();
    }

    // ---- Security logging -------------------------------------------------
    //
    // The three ways to be refused have to be told apart by whoever is looking
    // into it. AclDecision.denied(reason) does not carry the reason back to the
    // caller — the record holds only allowed, roles and permissions — so the log
    // is where that distinction has to live, and therefore where it is asserted.

    private ListAppender<ILoggingEvent> logAppender;

    private ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(ConfiguredAdminAclResolver.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        return logAppender;
    }

    @AfterEach
    void detachAppender() {
        if (logAppender != null) {
            ((Logger) LoggerFactory.getLogger(ConfiguredAdminAclResolver.class)).detachAppender(logAppender);
        }
    }

    private String loggedMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Tests that a key which is simply not the administrator's is logged as
     * such, and not as a configuration problem an operator would go hunting for.
     */
    @Test
    void shouldLogAnUnauthorisedKeyDistinctly() {
        // Given: a configured deployment and a different key
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);
        captureLogs();

        // When
        resolver.resolve(OTHER_HEX, OTHER_HEX);

        // Then
        assertThat(loggedMessages()).contains("admin_signin_rejected", "reason=not_authorised");
    }

    /**
     * Tests that an unconfigured deployment says so in the log, so the operator
     * looks at their configuration rather than at the person signing in.
     */
    @Test
    void shouldLogAMissingKeyDistinctly() {
        // Given: no administrator key configured
        ConfiguredAdminAclResolver resolver = resolverFor(null);
        captureLogs();

        // When
        resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

        // Then
        assertThat(loggedMessages()).contains("reason=no_admin_key_configured");
        assertThat(loggedMessages()).doesNotContain("reason=not_authorised");
    }

    /**
     * Tests that an unusable configured value is logged as its own problem,
     * since the fix differs from both of the above.
     */
    @Test
    void shouldLogAnUnreadableKeyDistinctly() {
        // Given: a configured value that is not a key
        ConfiguredAdminAclResolver resolver = resolverFor("not-a-key");
        captureLogs();

        // When
        resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

        // Then
        assertThat(loggedMessages()).contains("reason=admin_key_unreadable");
        assertThat(loggedMessages()).doesNotContain("reason=no_admin_key_configured");
    }

    /**
     * Tests that a successful sign-in is recorded too, with the public key and
     * the role granted. Every attempt appears in the log, not only the failures.
     */
    @Test
    void shouldLogASuccessfulSignIn() {
        // Given: the configured administrator
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);
        captureLogs();

        // When
        resolver.resolve(ADMIN_NPUB, ADMIN_HEX);

        // Then
        assertThat(loggedMessages()).contains("admin_signin_succeeded", ADMIN_HEX,
                AdminPermissions.SUPER_ADMIN);
    }
}
