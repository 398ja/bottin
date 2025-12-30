package xyz.tcheeric.bottin.web.dto;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for NIP-05 record operations.
 */
@Value
@Builder
public class Nip05RecordResponse {

    Long id;
    String nip05;
    String username;
    String domain;
    String pubkey;
    List<String> relays;
    boolean enabled;
    Instant createdAt;
    Instant updatedAt;

    /**
     * Creates a response from a Nip05RecordData.
     */
    public static Nip05RecordResponse from(Nip05RecordData data) {
        return Nip05RecordResponse.builder()
                .id(data.getId())
                .nip05(data.getNip05())
                .username(data.getUsername())
                .domain(data.getDomain())
                .pubkey(data.getPubkey())
                .relays(data.getRelays())
                .enabled(data.isEnabled())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .build();
    }
}
