package xyz.tcheeric.bottin.client.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nostr.crypto.bech32.Bech32;
import nostr.crypto.bech32.Bech32Prefix;
import nostr.crypto.schnorr.Schnorr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;
import xyz.tcheeric.nap.core.AclDecision;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.core.SessionStore;
import xyz.tcheeric.nap.server.AclResolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
@AutoConfigureTestRestTemplate
class ClientSessionGuardTest {

    private static final String PRINCIPAL_PUBKEY = "a".repeat(64);

    private static final int NIP98_KIND = 27235;

    private static final ObjectMapper JSON = new ObjectMapper();

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
     * Adding a relay changes the caller's own relay list, so it needs a caller.
     *
     * <p>With the follow and block routes gone (007 — a server holding no private key
     * cannot sign a list), this and {@link #shouldLetASignedInCallerAddARelay()} carry
     * the prefix-coverage property that four tests used to share: that a route needing
     * a principal is listed in {@code nap.protected-path-prefixes} in its own right,
     * never inferred from a neighbour's prefix. Narrowing this further leaves the
     * property pinned by {@code /api/v1/register} alone.
     */
    @Test
    void shouldRefuseAnAnonymousCallerAddingARelay() {
        var response = rest.postForEntity("/api/v1/relays", Map.of("url", "wss://relay.test"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * A caller holding a valid session is let through {@code /api/v1/relays}. This is
     * what {@code nap.protected-path-prefixes} governs: narrow it and the session filter
     * stops establishing a principal on this path, at which point the guard refuses
     * the signed-in user too — a 401 for everybody, with every anonymous test still
     * passing.
     */
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

        var guarded = rest.exchange("/api/v1/relays", HttpMethod.POST,
                new HttpEntity<>(Map.of("url", "wss://relay.test"), headers), String.class);
        var signIn = rest.exchange("/api/v1/auth/init", HttpMethod.POST,
                new HttpEntity<>(Map.of("npub", PRINCIPAL_NPUB), headers), String.class);

        assertThat(guarded.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(signIn.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Tests that the sign-in challenge reaches the browser in the snake_case form
     * {@code nap-client.js} reads ({@code challenge.challenge_id}), since a
     * {@code challengeId} here would leave no one able to complete a sign-in.
     */
    @Test
    void shouldIssueTheSignInChallengeInTheFormTheBrowserReads() {
        // Arrange: a well-formed key asking to sign in
        HttpHeaders headers = new HttpHeaders();

        // Act: request a challenge
        var challenge = rest.exchange("/api/v1/auth/init", HttpMethod.POST,
                new HttpEntity<>(Map.of("npub", PRINCIPAL_NPUB), headers), String.class);

        // Assert: issued, with the property names the client script depends on
        assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(challenge.getBody())
                .contains("\"challenge_id\"", "\"auth_url\"")
                .doesNotContain("\"challengeId\"");
    }

    /**
     * Tests that a completed sign-in answers in NAP's snake_case wire format
     * ({@code access_token}, {@code expires_at}). nap-spring returns its own
     * {@code AuthSuccessResponse}, named with Jackson 2's {@code @JsonNaming}; Spring
     * Boot 4's default Jackson 3 converter ignores that annotation and would answer
     * {@code accessToken}, changing the protocol every NAP client reads. The handshake is
     * performed for real, NIP-98 signature included, against the booted application.
     */
    @Test
    void shouldAnswerACompletedSignInInNapWireFormat() throws Exception {
        // Arrange: a key, and the challenge the server issues it
        byte[] privateKey = Schnorr.generatePrivateKey();
        String pubkey = HexFormat.of().formatHex(Schnorr.genPubKey(privateKey));
        String npub = Bech32.toBech32(Bech32Prefix.NPUB, pubkey);
        JsonNode challenge = JSON.readTree(rest.postForObject("/api/v1/auth/init",
                Map.of("npub", npub), String.class));

        // Act: sign it as nap-client.js does, and present it
        String body = JSON.writeValueAsString(Map.of("challenge_id", challenge.get("challenge_id").asText()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Nostr " + signedNip98Event(challenge, body, pubkey, privateKey));
        var completed = rest.exchange("/api/v1/auth/complete", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // Assert: signed in, and answered in the wire format clients read
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody())
                .contains("\"access_token\"", "\"expires_at\"", "\"token_type\"")
                .doesNotContain("\"accessToken\"", "\"expiresAt\"");
    }

    /** The NIP-98 event {@code NostrCrypto.signNip98Event} builds, base64-encoded. */
    private static String signedNip98Event(JsonNode challenge, String body, String pubkey, byte[] privateKey)
            throws Exception {
        long createdAt = Instant.now().getEpochSecond();
        List<List<String>> tags = List.of(
                List.of("u", challenge.get("auth_url").asText()),
                List.of("method", "POST"),
                List.of("payload", sha256Hex(body)),
                List.of("challenge", challenge.get("challenge").asText()),
                List.of("challenge_id", challenge.get("challenge_id").asText()));
        String serialized = JSON.writeValueAsString(List.of(0, pubkey, createdAt, NIP98_KIND, tags, ""));
        byte[] id = MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(StandardCharsets.UTF_8));
        byte[] signature = Schnorr.sign(id, privateKey, new byte[32]);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", HexFormat.of().formatHex(id));
        event.put("pubkey", pubkey);
        event.put("created_at", createdAt);
        event.put("kind", NIP98_KIND);
        event.put("tags", tags);
        event.put("content", "");
        event.put("sig", HexFormat.of().formatHex(signature));
        return Base64.getEncoder().encodeToString(JSON.writeValueAsBytes(event));
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
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
