package xyz.tcheeric.bottin.admin.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.service.AdminUserService;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies how a misconfigured deployment behaves.
 *
 * <p>The dangerous failure for this feature is not "the administrator cannot
 * sign in" — that is loud and gets fixed. It is a deployment that admits
 * somebody it should not, which is silent. So these tests assert the direction
 * of failure rather than a single refusal: when the key is missing or unusable,
 * <em>every</em> key is refused, not merely the one a happy-path test happens to
 * offer.
 *
 * <p>Distinct from ConfiguredAdminAclResolverTest, which covers the resolver's
 * decisions one at a time. This covers the property that no key gets through at
 * all, and that an operator is told at startup rather than only when somebody
 * fails to sign in.
 */
class AdminKeyConfigurationTest {

    /**
     * No stored administrators. These tests are about a misconfigured master
     * key, and an empty list is what makes the master key the only way in.
     */
    private final AdminUserService storedAdministrators = mock(AdminUserService.class);

    private ConfiguredAdminAclResolver resolverFor(String configuredKey) {
        return new ConfiguredAdminAclResolver(configuredKey, storedAdministrators);
    }

    private static final String ADMIN_NPUB =
            "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";

    /** Includes the key that *would* be the administrator had one been configured. */
    private static final String ADMIN_HEX =
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    private ListAppender<ILoggingEvent> logAppender;

    private void captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(ConfiguredAdminAclResolver.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
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
     * Tests that an unconfigured deployment refuses every key offered to it,
     * including the one that would have been the administrator. Nothing gets
     * through by default.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            ADMIN_HEX,
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            "0000000000000000000000000000000000000000000000000000000000000000",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    })
    void shouldAdmitNobodyWhenNoKeyIsConfigured(String pubkey) {
        // Given: a deployment with no administrator key
        ConfiguredAdminAclResolver resolver = resolverFor(null);

        // When & Then: whoever asks is refused
        assertThat(resolver.resolve(pubkey, pubkey).allowed()).isFalse();
    }

    /**
     * Tests the same for a configured value that is not a key. A deployment does
     * not become permissive because its configuration is broken.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            ADMIN_HEX,
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            "0000000000000000000000000000000000000000000000000000000000000000"
    })
    void shouldAdmitNobodyWhenTheConfiguredKeyIsUnusable(String pubkey) {
        // Given: a deployment whose configured value is not a public key
        ConfiguredAdminAclResolver resolver = resolverFor("not-a-key");

        // When & Then: whoever asks is refused
        assertThat(resolver.resolve(pubkey, pubkey).allowed()).isFalse();
    }

    /**
     * Tests that a null pubkey is refused rather than matching anything, since a
     * caller reaching the resolver without a proven key is a bug elsewhere and
     * must not be admitted here.
     */
    @Test
    void shouldRefuseWhenNoKeyWasProven() {
        // Given: a properly configured deployment
        ConfiguredAdminAclResolver resolver = resolverFor(ADMIN_NPUB);

        // When & Then
        assertThat(resolver.resolve(null, null).allowed()).isFalse();
    }

    /**
     * Tests that a missing key is reported when the deployment starts, so an
     * operator can discover it from the logs rather than only when somebody
     * fails to sign in.
     */
    @Test
    void shouldReportAMissingKeyAtStartup() {
        // Given: log capture in place before the resolver is built
        captureLogs();

        // When: the deployment starts with no administrator key
        resolverFor(null);

        // Then: it says so, and says what it means
        assertThat(loggedMessages()).contains("admin_key_configuration", "state=not_configured");
    }

    /**
     * Tests that an unusable configured value is reported at startup too, and
     * distinctly, since it is a different mistake with a different fix.
     */
    @Test
    void shouldReportAnUnusableKeyAtStartup() {
        // Given: log capture in place before the resolver is built
        captureLogs();

        // When: the deployment starts with a value that is not a key
        resolverFor("not-a-key");

        // Then
        assertThat(loggedMessages()).contains("admin_key_configuration", "state=unreadable");
        assertThat(loggedMessages()).doesNotContain("state=not_configured");
    }

    /**
     * Tests that a correctly configured deployment says so at startup, so a
     * silent log is not the only evidence that configuration worked.
     */
    @Test
    void shouldReportAUsableKeyAtStartup() {
        // Given: log capture in place before the resolver is built
        captureLogs();

        // When: the deployment starts with a usable administrator key
        resolverFor(ADMIN_NPUB);

        // Then
        assertThat(loggedMessages()).contains("admin_key_configuration", "state=configured");
    }
}
