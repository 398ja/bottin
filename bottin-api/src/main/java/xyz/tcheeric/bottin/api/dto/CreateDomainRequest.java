package xyz.tcheeric.bottin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Request DTO for registering a new domain.
 */
@Value
@Builder
@Jacksonized
public class CreateDomainRequest {

    @NotBlank(message = "Domain name is required")
    @Size(min = 1, max = 255, message = "Domain name must be between 1 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]$", message = "Invalid domain name format")
    String name;

    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "Owner public key must be a 64-character hex string")
    String ownerPubkey;
}
