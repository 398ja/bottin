package xyz.tcheeric.bottin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Request DTO for creating a new NIP-05 record.
 */
@Value
@Builder
@Jacksonized
public class CreateNip05RecordRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 1, max = 255, message = "Username must be between 1 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain letters, numbers, underscores, dots, and hyphens")
    String username;

    @NotBlank(message = "Domain is required")
    @Size(min = 1, max = 255, message = "Domain must be between 1 and 255 characters")
    String domain;

    @NotBlank(message = "Public key is required")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Public key must be a 64-character hex string")
    String pubkey;

    List<@Pattern(regexp = "^wss?://.*", message = "Each relay must be a valid WebSocket URL") String> relays;
}
