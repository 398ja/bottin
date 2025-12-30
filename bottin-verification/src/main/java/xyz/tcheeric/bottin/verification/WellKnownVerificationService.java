package xyz.tcheeric.bottin.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Duration;

/**
 * Service for verifying domain ownership via well-known file.
 *
 * <p>Fetches {@code https://<domain>/.well-known/bottin-verification.txt}
 * and checks that it contains the expected verification token.
 */
@Service
@Slf4j
public class WellKnownVerificationService {

    private static final String WELL_KNOWN_PATH = "/.well-known/bottin-verification.txt";

    private final WebClient webClient;

    @Value("${bottin.verification.http.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${bottin.verification.http.require-https:true}")
    private boolean requireHttps;

    public WellKnownVerificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(1024 * 10)) // 10KB max
                .build();
    }

    /**
     * Verifies a domain by checking the well-known verification file.
     *
     * @param domain the domain to verify
     * @param expectedToken the expected verification token
     * @return the verification result
     */
    public VerificationResult verify(String domain, String expectedToken) {
        String url = buildVerificationUrl(domain);
        log.debug("wellknown_verification_start domain={} url={}", domain, url);

        try {
            String content = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        throw new WebClientResponseException(
                                "HTTP " + response.statusCode().value(),
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                null, null, null);
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (content == null || content.isBlank()) {
                log.debug("wellknown_verification_empty domain={}", domain);
                return VerificationResult.failure(
                        VerificationMethod.WELL_KNOWN_FILE,
                        "Verification file is empty",
                        expectedToken,
                        null
                );
            }

            // Normalize content - trim whitespace and compare
            String normalizedContent = content.strip();
            if (normalizedContent.equals(expectedToken)) {
                log.info("wellknown_verification_success domain={}", domain);
                return VerificationResult.success(
                        VerificationMethod.WELL_KNOWN_FILE,
                        "Well-known file verified successfully"
                );
            }

            log.debug("wellknown_verification_token_mismatch domain={} expected={} found={}",
                    domain, expectedToken, normalizedContent);
            return VerificationResult.failure(
                    VerificationMethod.WELL_KNOWN_FILE,
                    "Verification file found but token does not match",
                    expectedToken,
                    normalizedContent.length() > 100
                            ? normalizedContent.substring(0, 100) + "..."
                            : normalizedContent
            );

        } catch (WebClientResponseException e) {
            log.debug("wellknown_verification_http_error domain={} status={}", domain, e.getStatusCode());
            return VerificationResult.failure(
                    VerificationMethod.WELL_KNOWN_FILE,
                    "HTTP error " + e.getStatusCode().value() + ": " + e.getStatusText()
            );
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (e.getCause() != null) {
                errorMessage = e.getCause().getMessage();
            }
            log.error("wellknown_verification_error domain={} error={}", domain, errorMessage);
            return VerificationResult.failure(
                    VerificationMethod.WELL_KNOWN_FILE,
                    "Failed to fetch verification file: " + errorMessage
            );
        }
    }

    /**
     * Builds the verification URL for the domain.
     */
    private String buildVerificationUrl(String domain) {
        String protocol = requireHttps ? "https" : "http";
        return protocol + "://" + domain + WELL_KNOWN_PATH;
    }

    /**
     * Generates instructions for setting up well-known file verification.
     *
     * @param domain the domain to verify
     * @param token the verification token
     * @return human-readable instructions
     */
    public String getSetupInstructions(String domain, String token) {
        String url = buildVerificationUrl(domain);
        return String.format("""
            Create a file at:

            %s

            The file should contain only this token (no extra whitespace):

            %s

            Make sure the file is accessible via HTTPS.
            """, url, token);
    }
}
