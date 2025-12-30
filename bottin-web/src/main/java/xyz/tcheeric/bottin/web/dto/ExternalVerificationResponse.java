package xyz.tcheeric.bottin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.verification.ExternalNip05VerificationResult;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for external NIP-05 verification.
 */
@Value
@Builder
@Schema(description = "Result of external NIP-05 verification")
public class ExternalVerificationResponse {

    @Schema(description = "The NIP-05 identifier that was verified", example = "alice@example.com")
    String nip05;

    @Schema(description = "Whether the verification was successful")
    boolean valid;

    @Schema(description = "The public key found for this identifier", example = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
    String pubkey;

    @Schema(description = "Relay URLs for this pubkey")
    List<String> relays;

    @Schema(description = "Human-readable message describing the result")
    String message;

    @Schema(description = "When the verification was performed")
    Instant verifiedAt;

    @Schema(description = "Whether this result was served from cache")
    boolean cached;

    /**
     * Creates a response from a verification result.
     */
    public static ExternalVerificationResponse from(ExternalNip05VerificationResult result) {
        return ExternalVerificationResponse.builder()
                .nip05(result.getNip05())
                .valid(result.isValid())
                .pubkey(result.getPubkey())
                .relays(result.getRelays())
                .message(result.getMessage())
                .verifiedAt(result.getVerifiedAt())
                .cached(result.isCached())
                .build();
    }
}
