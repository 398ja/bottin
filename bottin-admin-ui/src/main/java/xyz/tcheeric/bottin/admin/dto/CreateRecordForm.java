package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO for creating a new NIP-05 record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecordForm {

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "Username can only contain lowercase letters, numbers, underscore, and hyphen")
    private String username;

    @NotBlank(message = "Domain is required")
    private String domain;

    @NotBlank(message = "Public key is required")
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "Public key must be 64 hex characters")
    private String pubkey;

    @Builder.Default
    private List<String> relays = new ArrayList<>();

    private String relaysText;

    public List<String> getRelays() {
        if (relaysText != null && !relaysText.isBlank()) {
            return List.of(relaysText.split("\\s*[,\\n]\\s*"));
        }
        return relays != null ? relays : new ArrayList<>();
    }
}
