package xyz.tcheeric.bottin.api.dto;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.verification.VerificationResult;

import java.time.Instant;

/**
 * Response DTO for verification result.
 */
@Value
@Builder
public class VerificationResultResponse {

    boolean success;
    VerificationMethod method;
    String message;
    Instant verifiedAt;

    public static VerificationResultResponse from(VerificationResult result) {
        return VerificationResultResponse.builder()
                .success(result.isSuccess())
                .method(result.getMethod())
                .message(result.getMessage())
                .verifiedAt(result.getVerifiedAt())
                .build();
    }
}
