package xyz.tcheeric.bottin.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.reach.ProfileReach;

import java.time.Instant;

/**
 * API response carrying a profile's stored reach (follower count).
 */
@Value
@Builder
@Schema(description = "Stored reach (follower count) for a profile")
public class ProfileReachResponse {

    @Schema(description = "Canonical 64-character hex public key",
            example = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2")
    String pubkey;

    @Schema(description = "NIP-19 bech32 encoding of the same key",
            example = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m")
    String npub;

    @Schema(description = "Number of distinct current followers (may be 0)", example = "1542")
    long reachCount;

    @Schema(description = "true if gathered from a full set of relays; false if partial and may undercount")
    boolean complete;

    @Schema(description = "When this figure was last calculated", example = "2026-06-25T06:00:12Z")
    Instant calculatedAt;

    public static ProfileReachResponse from(ProfileReach reach) {
        return ProfileReachResponse.builder()
                .pubkey(reach.pubkey())
                .npub(reach.npub())
                .reachCount(reach.reachCount())
                .complete(reach.complete())
                .calculatedAt(reach.calculatedAt())
                .build();
    }
}
