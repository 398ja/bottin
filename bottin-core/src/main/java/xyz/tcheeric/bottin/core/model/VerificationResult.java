package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Represents the result of a domain verification attempt.
 */
@Value
@Builder(toBuilder = true)
public class VerificationResult {

    /**
     * The domain that was verified.
     */
    String domain;

    /**
     * Whether verification was successful.
     */
    boolean success;

    /**
     * The verification method used.
     */
    VerificationMethod method;

    /**
     * Error message if verification failed.
     */
    String errorMessage;

    /**
     * The raw response from the verification check (for debugging).
     */
    String rawResponse;

    /**
     * When the verification was performed.
     */
    Instant verifiedAt;

    /**
     * Creates a successful verification result.
     */
    public static VerificationResult success(String domain, VerificationMethod method) {
        return VerificationResult.builder()
                .domain(domain)
                .success(true)
                .method(method)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed verification result.
     */
    public static VerificationResult failure(String domain, VerificationMethod method, String errorMessage) {
        return VerificationResult.builder()
                .domain(domain)
                .success(false)
                .method(method)
                .errorMessage(errorMessage)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed verification result with raw response.
     */
    public static VerificationResult failure(String domain, VerificationMethod method,
                                              String errorMessage, String rawResponse) {
        return VerificationResult.builder()
                .domain(domain)
                .success(false)
                .method(method)
                .errorMessage(errorMessage)
                .rawResponse(rawResponse)
                .verifiedAt(Instant.now())
                .build();
    }
}
