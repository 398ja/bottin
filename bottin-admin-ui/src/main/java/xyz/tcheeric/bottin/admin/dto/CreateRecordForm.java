package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.tcheeric.bottin.core.nip05.HandleNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO for creating a new NIP-05 record.
 *
 * <p>The handle and domain are trimmed and lowercased as Spring binds them, before validation
 * (imani-wallet#342). Lombok generates every other accessor; it does not replace these two.
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

    public void setUsername(String username) {
        this.username = HandleNormalizer.normalizeHandle(username);
    }

    public void setDomain(String domain) {
        this.domain = HandleNormalizer.normalizeDomain(domain);
    }

    public List<String> getRelays() {
        if (relaysText != null && !relaysText.isBlank()) {
            return List.of(relaysText.split("\\s*[,\\n]\\s*"));
        }
        return relays != null ? relays : new ArrayList<>();
    }
}
