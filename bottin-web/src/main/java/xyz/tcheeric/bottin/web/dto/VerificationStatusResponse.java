package xyz.tcheeric.bottin.web.dto;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.verification.VerificationStatus;

import java.time.Instant;

/**
 * Response DTO for verification status.
 */
@Value
@Builder
public class VerificationStatusResponse {

    Long domainId;
    String domainName;
    boolean verified;
    Instant verifiedAt;
    VerificationMethod method;
    boolean hasPendingVerification;

    public static VerificationStatusResponse from(VerificationStatus status) {
        return VerificationStatusResponse.builder()
                .domainId(status.getDomainId())
                .domainName(status.getDomainName())
                .verified(status.isVerified())
                .verifiedAt(status.getVerifiedAt())
                .method(status.getMethod())
                .hasPendingVerification(status.isHasPendingVerification())
                .build();
    }
}
