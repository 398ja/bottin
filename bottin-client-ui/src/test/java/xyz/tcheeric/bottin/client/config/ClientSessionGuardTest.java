package xyz.tcheeric.bottin.client.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.core.SessionStore;
import xyz.tcheeric.nap.server.AclResolver;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * What the client API refuses an anonymous caller, and what it still serves them.
 *
 * <p>Booted rather than sliced, and named {@code *Test} rather than {@code *IT}, because
 * both matter: {@code @WebMvcTest} does not load nap's auto-configuration, so
 * {@code @RequiresSession} is not enforced in a slice and a slice would pass no matter
 * what the annotations said; and this module binds failsafe only under {@code -Pit}, so
 * an {@code *IT} would never run in the ordinary build.
 *
 * <p>Overrides no property on purpose: it runs on the deployed {@code application.yml},
 * where pinning the guard's own configuration would prove it against a setup no
 * deployment uses.
 *
 * <p>Refusing an anonymous caller is only half of it, and the half that cannot fail:
 * a handler outside {@code nap.protected-path-prefixes} is never given a principal, so
 * {@code @RequiresSession} refuses <em>everyone</em> there, signed in or not, and a
 * suite that only sends anonymous requests reads that as success. The prefix list is
 * therefore pinned by letting a real session through.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientSessionGuardTest {

    private static final String PRINCIPAL_PUBKEY = "a".repeat(64);

    private static final String PRINCIPAL_NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SessionStore sessionStore;

    @MockitoBean
    private DirectorySettingsClient settingsClient;

    @MockitoBean
    private DirectoryRegistrationService registrationService;

    @MockitoBean
    private AclResolver aclResolver;

    @BeforeEach
    void configureDeployment() {
        when(settingsClient.current())
                .thenReturn(new DirectorySettings(null, List.of("wss://deployment.test"), List.of()));
        when(aclResolver.resolve(any(), any())).thenReturn(AclDecision.allowed(List.of(), List.of()));
    }

    /**
     * The follow routes act on one person's follow list, so an anonymous caller is
     * refused rather than served an empty one.
     */
    @Test
    void shouldRefuseAnAnonymousCallerOnTheFollowRoutes() {
        var response = rest.postForEntity("/api/v1/follow", Map.of("pubkey", PRINCIPAL_PUBKEY), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Guarding by annotation rather than by URL pattern is what closes this one:
     * {@code /api/v1/unfollow} matches neither {@code /api/v1/follow} nor
     * {@code /api/v1/follow/*}, so the prefix list that read as though it covered the
     * follow routes left this one open to anyone.
     */
    @Test
    void shouldRefuseAnAnonymousCallerOnUnfollow() {
        var response = rest.postForEntity("/api/v1/unfollow", Map.of("pubkey", PRINCIPAL_PUBKEY), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Adding a relay changes the caller's own relay list, so it needs a caller.
     */
    @Test
    void shouldRefuseAnAnonymousCallerAddingARelay() {
        var response = rest.postForEntity("/api/v1/relays", Map.of("url", "wss://relay.test"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * A caller holding a valid session is let through. This is what
     * {@code nap.protected-path-prefixes} governs: narrow it and the session filter
     * stops establishing a principal on this path, at which point the guard refuses
     * the signed-in user too — a 401 for everybody, with every anonymous test still
     * passing.
     */
    @Test
    void shouldLetASignedInCallerThroughTheFollowRoutes() {
        var response = signedInPost("/api/v1/follow", Map.of("pubkey", PRINCIPAL_PUBKEY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Pins {@code /api/v1/unfollow}, which no other entry in the prefix list covers. */
    @Test
    void shouldLetASignedInCallerThroughUnfollow() {
        var response = signedInPost("/api/v1/unfollow", Map.of("pubkey", PRINCIPAL_PUBKEY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Pins {@code /api/v1/relays}. */
    @Test
    void shouldLetASignedInCallerAddARelay() {
        var response = signedInPost("/api/v1/relays", Map.of("url", "wss://relay.test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Pins {@code /api/v1/register}, which guards itself by reading the principal rather
     * than by annotation — so leaving it off the prefix list would end every onboarding
     * with a 401 the handler raises against a session the caller genuinely holds.
     */
    @Test
    void shouldLetASignedInCallerRegisterAHandle() throws Exception {
        when(registrationService.register(eq("newcomer"), eq(PRINCIPAL_PUBKEY), any()))
                .thenReturn("newcomer@imani.test");

        var response = signedInPost("/api/v1/register", Map.of("username", "newcomer", "relays", List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("newcomer@imani.test");
    }

    /**
     * A resolver that denies a session the filter can read answers 403, and that must not
     * reach the sign-in endpoints: a denied caller still holding a cookie has to be able
     * to authenticate as someone else, and cannot if the route that authenticates them is
     * the one turning them away. Keeping {@code /api/v1/auth} off the prefix list is what
     * separates the two.
     */
    @Test
    void shouldLeaveSignInReachableWhenTheResolverDeniesTheSession() {
        when(aclResolver.resolve(any(), any())).thenReturn(AclDecision.denied("suspended", false));
        HttpHeaders headers = signedInHeaders();

        var guarded = rest.exchange("/api/v1/follow", HttpMethod.POST,
                new HttpEntity<>(Map.of("pubkey", PRINCIPAL_PUBKEY), headers), String.class);
        var signIn = rest.exchange("/api/v1/auth/init", HttpMethod.POST,
                new HttpEntity<>(Map.of("npub", PRINCIPAL_NPUB), headers), String.class);

        assertThat(guarded.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(signIn.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> signedInPost(String path, Map<String, Object> body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, signedInHeaders()), String.class);
    }

    /**
     * The cookie a completed sign-in would have left in the browser.
     *
     * <p>A fresh session id every time, because {@code NapSessionFilter} caches the ACL
     * decision against it for {@code nap.acl-refresh-interval}. Reuse one and a test that
     * denies the resolver is answered from the previous test's "allowed".
     */
    private HttpHeaders signedInHeaders() {
        long now = Instant.now().getEpochSecond();
        String distinct = UUID.randomUUID().toString();
        SessionRecord record = SessionRecord.create(
                "session-" + distinct, "challenge-" + distinct, "access-token-" + distinct,
                PRINCIPAL_NPUB, PRINCIPAL_PUBKEY, List.of(), List.of(), now, now + 3600);
        sessionStore.createForChallenge(record);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "client_session=" + record.sessionId());
        return headers;
    }

    /**
     * The deployment's relays stay readable without a session. A signed-out reader
     * needs them to read a profile at {@code /profile/{pubkey}}, which is a public
     * page, and they describe the deployment rather than any user.
     */
    @Test
    void shouldServeTheDeploymentRelaysToAnAnonymousCaller() {
        var response = rest.getForEntity("/api/v1/relays/system", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("wss://deployment.test");
    }
}
