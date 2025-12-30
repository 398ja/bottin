package xyz.tcheeric.bottin.web.dto;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * Response DTO for domain operations.
 */
@Value
@Builder
public class DomainResponse {

    Long id;
    String name;
    boolean verified;
    VerificationMethod verificationMethod;
    Instant verifiedAt;
    String ownerPubkey;
    Instant createdAt;
    Instant updatedAt;
    int recordCount;

    /**
     * Creates a response from a DomainData.
     */
    public static DomainResponse from(DomainData data) {
        return DomainResponse.builder()
                .id(data.getId())
                .name(data.getName())
                .verified(data.isVerified())
                .verificationMethod(data.getVerificationMethod())
                .verifiedAt(data.getVerifiedAt())
                .ownerPubkey(data.getOwnerPubkey())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .recordCount(data.getRecordCount())
                .build();
    }
}
