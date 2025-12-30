package xyz.tcheeric.bottin.verification;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * Represents a verification challenge for domain ownership.
 */
@Value
@Builder
public class VerificationChallenge {

    /**
     * The domain ID.
     */
    Long domainId;

    /**
     * The domain name.
     */
    String domainName;

    /**
     * The verification method to use.
     */
    VerificationMethod method;

    /**
     * The verification token.
     */
    String token;

    /**
     * Human-readable setup instructions.
     */
    String instructions;

    /**
     * When this challenge was created.
     */
    Instant createdAt;

    /**
     * Whether the domain is already verified.
     */
    boolean alreadyVerified;

    /**
     * Creates a challenge indicating the domain is already verified.
     */
    public static VerificationChallenge alreadyVerified(String domainName) {
        return VerificationChallenge.builder()
                .domainName(domainName)
                .alreadyVerified(true)
                .instructions("This domain is already verified.")
                .createdAt(Instant.now())
                .build();
    }
}
