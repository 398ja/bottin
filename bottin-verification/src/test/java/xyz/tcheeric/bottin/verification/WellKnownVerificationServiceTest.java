package xyz.tcheeric.bottin.verification;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WellKnownVerificationService.
 * Uses WireMock to mock HTTP responses for verification testing.
 */
class WellKnownVerificationServiceTest {

    private WireMockServer wireMockServer;
    private WellKnownVerificationService verificationService;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        verificationService = new WellKnownVerificationService(WebClient.builder());
        ReflectionTestUtils.setField(verificationService, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(verificationService, "requireHttps", false);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    /**
     * Tests successful verification when file contains the expected token.
     */
    @Test
    void shouldReturnSuccessWhenTokenMatches() {
        // Arrange: Mock server returns expected token
        String token = "test-token-123";
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(token)));

        // Act: Verify against localhost with WireMock port
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), token);

        // Assert: Verification succeeds
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMethod()).isEqualTo(VerificationMethod.WELL_KNOWN_FILE);
        assertThat(result.getMessage()).contains("verified successfully");
    }

    /**
     * Tests that whitespace around the token is ignored.
     */
    @Test
    void shouldSucceedWhenTokenHasTrailingWhitespace() {
        // Arrange: Mock server returns token with whitespace
        String token = "test-token-123";
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("  " + token + "  \n")));

        // Act: Verify against localhost
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), token);

        // Assert: Verification succeeds despite whitespace
        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * Tests failure when token does not match.
     */
    @Test
    void shouldReturnFailureWhenTokenMismatch() {
        // Arrange: Mock server returns wrong token
        String expectedToken = "expected-token";
        String actualToken = "wrong-token";
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(actualToken)));

        // Act: Verify with expected token
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), expectedToken);

        // Assert: Verification fails with mismatch message
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("does not match");
        assertThat(result.getFoundValue()).isEqualTo(actualToken);
    }

    /**
     * Tests failure when verification file is empty.
     */
    @Test
    void shouldReturnFailureWhenFileIsEmpty() {
        // Arrange: Mock server returns empty content
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        // Act: Verify
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), "some-token");

        // Assert: Verification fails
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("empty");
    }

    /**
     * Tests failure when file is not found (404).
     */
    @Test
    void shouldReturnFailureWhenFileNotFound() {
        // Arrange: Mock server returns 404
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // Act: Verify
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), "some-token");

        // Assert: Verification fails with HTTP error
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("HTTP error");
        assertThat(result.getMessage()).contains("404");
    }

    /**
     * Tests failure when server returns 500.
     */
    @Test
    void shouldReturnFailureOnServerError() {
        // Arrange: Mock server returns 500
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // Act: Verify
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), "some-token");

        // Assert: Verification fails with HTTP error
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("HTTP error");
        assertThat(result.getMessage()).contains("500");
    }

    /**
     * Tests that setup instructions include the correct URL.
     */
    @Test
    void shouldGenerateInstructionsWithCorrectUrl() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "test-token";

        // Act: Generate instructions
        ReflectionTestUtils.setField(verificationService, "requireHttps", true);
        String instructions = verificationService.getSetupInstructions(domain, token);

        // Assert: Instructions contain the correct URL
        assertThat(instructions).contains("https://example.com/.well-known/nostr-verification.txt");
    }

    /**
     * Tests that setup instructions include the token.
     */
    @Test
    void shouldGenerateInstructionsWithToken() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "my-verification-token";

        // Act: Generate instructions
        String instructions = verificationService.getSetupInstructions(domain, token);

        // Assert: Instructions contain the token
        assertThat(instructions).contains("my-verification-token");
    }

    /**
     * Tests that long content in the response is truncated in error message.
     */
    @Test
    void shouldTruncateLongContentInErrorMessage() {
        // Arrange: Mock server returns very long content
        String longContent = "a".repeat(200);
        wireMockServer.stubFor(get(urlEqualTo("/.well-known/nostr-verification.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(longContent)));

        // Act: Verify with different token
        VerificationResult result = verificationService.verify(
                "localhost:" + wireMockServer.port(), "expected-token");

        // Assert: Found value is truncated
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFoundValue()).hasSize(103); // 100 + "..."
        assertThat(result.getFoundValue()).endsWith("...");
    }
}
