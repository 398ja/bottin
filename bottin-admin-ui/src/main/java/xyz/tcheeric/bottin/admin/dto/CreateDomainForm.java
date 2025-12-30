package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form DTO for creating a new domain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDomainForm {

    @NotBlank(message = "Domain name is required")
    @Pattern(regexp = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z]{2,})+$",
            message = "Invalid domain name format")
    private String name;

    @Pattern(regexp = "^([0-9a-f]{64})?$", message = "Public key must be 64 hex characters or empty")
    private String ownerPubkey;
}
