package xyz.tcheeric.bottin.it;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import xyz.tcheeric.bottin.persistence.repository.VerificationLogRepository;
import xyz.tcheeric.bottin.verification.ExternalNip05VerificationResult;
import xyz.tcheeric.bottin.verification.ExternalNip05Verifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-05: External NIP-05 Verifier Integration Tests.
 * Verifies external NIP-05 verification with WireMock and database logging.
 */
class ExternalNip05VerifierIT extends BaseIntegrationTest {

    @Autowired
    private ExternalNip05Verifier externalNip05Verifier;

    @Autowired
    private VerificationLogRepository verificationLogRepository;

    @Autowired(required = false)
    private CacheManager cacheManager;

    private WireMockServer wireMockServer;

    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @BeforeEach
    void setUp() {
        verificationLogRepository.deleteAll();

        // Clear cache before each test
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }

        // Start WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    /**
     * Tests that a valid NIP-05 JSON response is parsed correctly.
     */
    @Test
    void shouldParseValidNip05JsonAndExtractPubkey() {
        // Arrange: Mock external server returning valid NIP-05 JSON
        String validJson = """
                {
                    "names": {
                        "alice": "%s"
                    },
                    "relays": {
                        "%s": ["wss://relay.example.com"]
                    }
                }
                """.formatted(TEST_PUBKEY, TEST_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(validJson)));

        String testNip05 = "alice@localhost:" + wireMockServer.port();

        // Act: Verify the external NIP-05
        ExternalNip05VerificationResult result = externalNip05Verifier.verify(testNip05);

        // Assert: Pubkey should be extracted correctly
        assertThat(result.isValid()).isTrue();
        assertThat(result.getPubkey()).isEqualTo(TEST_PUBKEY);
        assertThat(result.getRelays()).contains("wss://relay.example.com");
    }

    /**
     * Tests that verification result is logged to the database.
     */
    @Test
    void shouldLogVerificationResultToDatabase() {
        // Arrange: Mock server
        String validJson = """
                {
                    "names": {
                        "bob": "%s"
                    }
                }
                """.formatted(TEST_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(validJson)));

        String testNip05 = "bob@localhost:" + wireMockServer.port();

        // Act: Verify
        externalNip05Verifier.verify(testNip05);

        // Assert: Verification should be logged
        assertThat(verificationLogRepository.count()).isGreaterThan(0);
    }

    /**
     * Tests that malformed JSON returns verification failure.
     */
    @Test
    void shouldReturnFailureForMalformedJson() {
        // Arrange: Mock server returning malformed JSON
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ invalid json }")));

        String testNip05 = "charlie@localhost:" + wireMockServer.port();

        // Act: Verify
        ExternalNip05VerificationResult result = externalNip05Verifier.verify(testNip05);

        // Assert: Should return failure
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isNotEmpty();
    }

    /**
     * Tests that 404 response returns verification failure.
     */
    @Test
    void shouldReturnFailureFor404Response() {
        // Arrange: Mock server returning 404
        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(404)));

        String testNip05 = "notfound@localhost:" + wireMockServer.port();

        // Act: Verify
        ExternalNip05VerificationResult result = externalNip05Verifier.verify(testNip05);

        // Assert: Should return failure
        assertThat(result.isValid()).isFalse();
    }

    /**
     * Tests that username not in response returns failure.
     */
    @Test
    void shouldReturnFailureWhenUsernameNotInResponse() {
        // Arrange: Mock server returning JSON without the requested username
        String jsonWithoutUser = """
                {
                    "names": {
                        "other": "%s"
                    }
                }
                """.formatted(TEST_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonWithoutUser)));

        String testNip05 = "missing@localhost:" + wireMockServer.port();

        // Act: Verify
        ExternalNip05VerificationResult result = externalNip05Verifier.verify(testNip05);

        // Assert: Should return failure
        assertThat(result.isValid()).isFalse();
    }

    /**
     * Tests that invalid NIP-05 format returns failure without making HTTP request.
     */
    @Test
    void shouldReturnFailureForInvalidNip05Format() {
        // Act: Verify with invalid format
        ExternalNip05VerificationResult result = externalNip05Verifier.verify("invalid-format");

        // Assert: Should return failure
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Invalid");

        // No HTTP request should have been made
        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    /**
     * Tests caching - second call should not hit the external server.
     */
    @Test
    void shouldCacheVerificationResults() {
        // Skip if cache is not available
        if (cacheManager == null) {
            return;
        }

        // Arrange: Mock server
        String validJson = """
                {
                    "names": {
                        "cached": "%s"
                    }
                }
                """.formatted(TEST_PUBKEY);

        wireMockServer.stubFor(get(urlPathEqualTo("/.well-known/nostr.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(validJson)));

        String testNip05 = "cached@localhost:" + wireMockServer.port();

        // Act: First call
        ExternalNip05VerificationResult firstResult = externalNip05Verifier.verify(testNip05);

        // Act: Second call (should hit cache)
        ExternalNip05VerificationResult secondResult = externalNip05Verifier.verify(testNip05);

        // Assert: Both should succeed
        assertThat(firstResult.isValid()).isTrue();
        assertThat(secondResult.isValid()).isTrue();

        // Only one request should have been made
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/.well-known/nostr.json")));
    }
}
