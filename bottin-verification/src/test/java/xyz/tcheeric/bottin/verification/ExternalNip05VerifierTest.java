package xyz.tcheeric.bottin.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import xyz.tcheeric.bottin.persistence.repository.VerificationLogRepository;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExternalNip05Verifier using WireMock for HTTP mocking.
 */
@ExtendWith(MockitoExtension.class)
class ExternalNip05VerifierTest {

    private static final String VALID_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    private WireMockServer wireMockServer;
    private ExternalNip05Verifier verifier;
    private String testDomain;

    @Mock
    private VerificationLogRepository verificationLogRepository;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        testDomain = "localhost:" + wireMockServer.port();

        WebClient.Builder webClientBuilder = WebClient.builder();

        verifier = new ExternalNip05Verifier(
                webClientBuilder,
                new ObjectMapper(),
                verificationLogRepository
        );

        // Configure for testing - use HTTP instead of HTTPS
        ReflectionTestUtils.setField(verifier, "requireHttps", false);
        ReflectionTestUtils.setField(verifier, "timeoutSeconds", 5);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    /**
     * Tests that a valid NIP-05 identifier is verified successfully when
     * the remote server returns a valid nostr.json response.
     */
    @Test
    void shouldVerifyValidNip05Successfully() {
        // Arrange
        String nostrJson = """
            {
                "names": {
                    "alice": "%s"
                },
                "relays": {
                    "%s": ["wss://relay.example.com", "wss://relay2.example.com"]
                }
            }
            """.formatted(VALID_PUBKEY, VALID_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isTrue();
        assertThat(result.getPubkey()).isEqualTo(VALID_PUBKEY);
        assertThat(result.getRelays()).containsExactly("wss://relay.example.com", "wss://relay2.example.com");
        assertThat(result.getMessage()).contains("verified successfully");
    }

    /**
     * Tests that verification fails gracefully when the username is not found
     * in the nostr.json response.
     */
    @Test
    void shouldReturnFailureWhenUsernameNotFound() {
        // Arrange
        String nostrJson = """
            {
                "names": {
                    "bob": "%s"
                }
            }
            """.formatted(VALID_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("not found");
    }

    /**
     * Tests that verification fails when the remote server returns an HTTP error.
     */
    @Test
    void shouldReturnFailureOnHttpError() {
        // Arrange
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found")));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("HTTP error");
    }

    /**
     * Tests that verification fails when the response is not valid JSON.
     */
    @Test
    void shouldReturnFailureOnInvalidJson() {
        // Arrange
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not valid json")));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Invalid JSON");
    }

    /**
     * Tests that verification fails when the pubkey is not a valid hex string.
     */
    @Test
    void shouldReturnFailureOnInvalidPubkey() {
        // Arrange
        String nostrJson = """
            {
                "names": {
                    "alice": "not-a-valid-pubkey"
                }
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Invalid pubkey");
    }

    /**
     * Tests that verification fails when the response is missing the 'names' object.
     */
    @Test
    void shouldReturnFailureWhenNamesObjectMissing() {
        // Arrange
        String nostrJson = """
            {
                "relays": {}
            }
            """;

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("missing 'names'");
    }

    /**
     * Tests that verification handles empty relay list gracefully.
     */
    @Test
    void shouldHandleEmptyRelayList() {
        // Arrange
        String nostrJson = """
            {
                "names": {
                    "alice": "%s"
                }
            }
            """.formatted(VALID_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isTrue();
        assertThat(result.getRelays()).isEmpty();
    }

    /**
     * Tests that invalid NIP-05 format is rejected.
     */
    @Test
    void shouldRejectInvalidNip05Format() {
        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("invalid-format");

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Invalid NIP-05 format");
    }

    /**
     * Tests that null NIP-05 is rejected.
     */
    @Test
    void shouldRejectNullNip05() {
        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache(null);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("required");
    }

    /**
     * Tests that blank NIP-05 is rejected.
     */
    @Test
    void shouldRejectBlankNip05() {
        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("   ");

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("required");
    }

    /**
     * Tests the NIP-05 parsing logic correctly normalizes case.
     */
    @Test
    void shouldParseNip05CorrectlyAndNormalizeCase() {
        // Act
        ExternalNip05Verifier.ParsedNip05 parsed = verifier.parseNip05("Alice@Example.COM");

        // Assert
        assertThat(parsed).isNotNull();
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.domain()).isEqualTo("example.com");
    }

    /**
     * Tests that invalid relay URLs are filtered out.
     */
    @Test
    void shouldFilterInvalidRelayUrls() {
        // Arrange
        String nostrJson = """
            {
                "names": {
                    "alice": "%s"
                },
                "relays": {
                    "%s": ["wss://valid.relay", "http://invalid.relay", "ws://also-valid.relay"]
                }
            }
            """.formatted(VALID_PUBKEY, VALID_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nostrJson)));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isTrue();
        assertThat(result.getRelays()).containsExactly("wss://valid.relay", "ws://also-valid.relay");
    }

    /**
     * Tests that usernames with special characters (underscore, hyphen, dot) are parsed correctly.
     */
    @Test
    void shouldParseUsernamesWithSpecialCharacters() {
        // Act & Assert
        assertThat(verifier.parseNip05("alice_bob@example.com")).isNotNull();
        assertThat(verifier.parseNip05("alice-bob@example.com")).isNotNull();
        assertThat(verifier.parseNip05("alice.bob@example.com")).isNotNull();
        assertThat(verifier.parseNip05("alice_123@example.com")).isNotNull();
    }

    /**
     * Tests that invalid usernames are rejected.
     */
    @Test
    void shouldRejectInvalidUsernames() {
        // Act & Assert - special characters not allowed
        assertThat(verifier.parseNip05("alice!@example.com")).isNull();
        assertThat(verifier.parseNip05("alice@bob@example.com")).isNull();
        assertThat(verifier.parseNip05("@example.com")).isNull();
        assertThat(verifier.parseNip05("alice@")).isNull();
    }

    /**
     * Tests verification handles empty response body.
     */
    @Test
    void shouldReturnFailureOnEmptyResponse() {
        // Arrange
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Empty response");
    }

    /**
     * Tests that server error 500 is handled gracefully.
     */
    @Test
    void shouldReturnFailureOnServerError() {
        // Arrange
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // Act
        ExternalNip05VerificationResult result = verifier.verifyNoCache("alice@" + testDomain);

        // Assert
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("HTTP error 500");
    }
}
