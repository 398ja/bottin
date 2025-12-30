package xyz.tcheeric.bottin.web.dto;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.verification.VerificationChallenge;

import java.time.Instant;

/**
 * Response DTO for verification challenge.
 */
@Value
@Builder
public class VerificationChallengeResponse {

    Long domainId;
    String domainName;
    VerificationMethod method;
    String token;
    String instructions;
    Instant createdAt;
    boolean alreadyVerified;

    public static VerificationChallengeResponse from(VerificationChallenge challenge) {
        return VerificationChallengeResponse.builder()
                .domainId(challenge.getDomainId())
                .domainName(challenge.getDomainName())
                .method(challenge.getMethod())
                .token(challenge.getToken())
                .instructions(challenge.getInstructions())
                .createdAt(challenge.getCreatedAt())
                .alreadyVerified(challenge.isAlreadyVerified())
                .build();
    }
}
