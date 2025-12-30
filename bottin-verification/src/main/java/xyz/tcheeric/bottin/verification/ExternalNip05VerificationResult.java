package xyz.tcheeric.bottin.verification;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Result of an external NIP-05 verification.
 */
@Value
@Builder
public class ExternalNip05VerificationResult {

    /**
     * The NIP-05 identifier that was verified.
     */
    String nip05;

    /**
     * Whether the verification was successful.
     */
    boolean valid;

    /**
     * The public key found for this identifier (null if not found).
     */
    String pubkey;

    /**
     * Relay URLs for this pubkey (empty if not found or not specified).
     */
    @Builder.Default
    List<String> relays = List.of();

    /**
     * Human-readable message describing the result.
     */
    String message;

    /**
     * When the verification was performed.
     */
    Instant verifiedAt;

    /**
     * Whether this result was served from cache.
     */
    boolean cached;

    /**
     * Creates a successful verification result.
     */
    public static ExternalNip05VerificationResult success(String nip05, String pubkey, List<String> relays) {
        return ExternalNip05VerificationResult.builder()
                .nip05(nip05)
                .valid(true)
                .pubkey(pubkey)
                .relays(relays != null ? relays : List.of())
                .message("NIP-05 identifier verified successfully")
                .verifiedAt(Instant.now())
                .cached(false)
                .build();
    }

    /**
     * Creates a failed verification result.
     */
    public static ExternalNip05VerificationResult failure(String nip05, String message) {
        return ExternalNip05VerificationResult.builder()
                .nip05(nip05)
                .valid(false)
                .message(message)
                .verifiedAt(Instant.now())
                .cached(false)
                .build();
    }

    /**
     * Returns this result marked as cached.
     */
    public ExternalNip05VerificationResult asCached() {
        return ExternalNip05VerificationResult.builder()
                .nip05(this.nip05)
                .valid(this.valid)
                .pubkey(this.pubkey)
                .relays(this.relays)
                .message(this.message)
                .verifiedAt(this.verifiedAt)
                .cached(true)
                .build();
    }
}
