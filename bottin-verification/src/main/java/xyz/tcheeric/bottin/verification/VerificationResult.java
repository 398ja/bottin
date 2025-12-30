package xyz.tcheeric.bottin.verification;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * Result of a domain verification attempt.
 */
@Value
@Builder
public class VerificationResult {

    /**
     * Whether the verification was successful.
     */
    boolean success;

    /**
     * The verification method used.
     */
    VerificationMethod method;

    /**
     * Human-readable message describing the result.
     */
    String message;

    /**
     * When the verification was performed.
     */
    Instant verifiedAt;

    /**
     * The value found during verification (for diagnostics).
     */
    String foundValue;

    /**
     * The expected value for verification.
     */
    String expectedValue;

    /**
     * Creates a successful verification result.
     */
    public static VerificationResult success(VerificationMethod method, String message) {
        return VerificationResult.builder()
                .success(true)
                .method(method)
                .message(message)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed verification result.
     */
    public static VerificationResult failure(VerificationMethod method, String message) {
        return VerificationResult.builder()
                .success(false)
                .method(method)
                .message(message)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed verification result with diagnostic information.
     */
    public static VerificationResult failure(VerificationMethod method, String message,
                                              String expectedValue, String foundValue) {
        return VerificationResult.builder()
                .success(false)
                .method(method)
                .message(message)
                .expectedValue(expectedValue)
                .foundValue(foundValue)
                .verifiedAt(Instant.now())
                .build();
    }
}
