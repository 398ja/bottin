package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;

import java.util.ArrayList;
import java.util.List;

/**
 * Form DTO for updating a NIP-05 record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRecordForm {

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

    public static UpdateRecordForm from(Nip05RecordData record) {
        return UpdateRecordForm.builder()
                .pubkey(record.getPubkey())
                .relays(record.getRelays())
                .relaysText(String.join("\n", record.getRelays()))
                .build();
    }
}
