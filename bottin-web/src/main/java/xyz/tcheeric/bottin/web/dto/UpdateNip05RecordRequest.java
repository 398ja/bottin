package xyz.tcheeric.bottin.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Request DTO for updating an existing NIP-05 record.
 * All fields are optional - only provided fields will be updated.
 */
@Value
@Builder
@Jacksonized
public class UpdateNip05RecordRequest {

    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Public key must be a 64-character hex string")
    String pubkey;

    List<@Pattern(regexp = "^wss?://.*", message = "Each relay must be a valid WebSocket URL") String> relays;

    Boolean enabled;
}
