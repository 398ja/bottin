package xyz.tcheeric.bottin.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable value object representing a NIP-05 record in Bottin.
 * Extends the base Nip05Record with additional persistence metadata.
 */
@Value
@Builder(toBuilder = true)
public class Nip05RecordData {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Unique identifier for the record.
     */
    Long id;

    /**
     * The domain ID this record belongs to.
     */
    Long domainId;

    /**
     * The username part of the NIP-05 identifier.
     */
    String username;

    /**
     * The domain part of the NIP-05 identifier.
     */
    String domain;

    /**
     * The public key (64-char hex) associated with this identifier.
     */
    String pubkey;

    /**
     * JSON array of relay URLs.
     */
    String relaysJson;

    /**
     * Whether this record is enabled.
     */
    boolean enabled;

    /**
     * When the record was created.
     */
    Instant createdAt;

    /**
     * When the record was last updated.
     */
    Instant updatedAt;

    /**
     * Returns the full NIP-05 identifier (username@domain).
     */
    @JsonIgnore
    public String getNip05() {
        return username + "@" + domain;
    }

    /**
     * Returns the relay URLs as a list.
     */
    @JsonIgnore
    public List<String> getRelays() {
        if (relaysJson == null || relaysJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(relaysJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a new NIP-05 record.
     */
    public static Nip05RecordData createNew(String username, String domain, String pubkey,
                                             Long domainId, List<String> relays) {
        String relaysJson;
        try {
            relaysJson = relays == null || relays.isEmpty()
                    ? "[]"
                    : MAPPER.writeValueAsString(relays);
        } catch (JsonProcessingException e) {
            relaysJson = "[]";
        }

        Instant now = Instant.now();
        return Nip05RecordData.builder()
                .domainId(domainId)
                .username(username.toLowerCase())
                .domain(domain.toLowerCase())
                .pubkey(pubkey)
                .relaysJson(relaysJson)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Returns this record with updated relays.
     */
    public Nip05RecordData withRelays(List<String> relays) {
        String newRelaysJson;
        try {
            newRelaysJson = relays == null || relays.isEmpty()
                    ? "[]"
                    : MAPPER.writeValueAsString(relays);
        } catch (JsonProcessingException e) {
            newRelaysJson = "[]";
        }
        return this.toBuilder()
                .relaysJson(newRelaysJson)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns this record with enabled status toggled.
     */
    public Nip05RecordData withEnabled(boolean enabled) {
        return this.toBuilder()
                .enabled(enabled)
                .updatedAt(Instant.now())
                .build();
    }
}
