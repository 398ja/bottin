package xyz.tcheeric.bottin.reach;

import nostr.crypto.bech32.Bech32;
import nostr.crypto.bech32.Bech32Prefix;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.core.exception.InvalidPubkeyException;

import java.util.regex.Pattern;

/**
 * Converts and validates profile identifiers between NIP-19 {@code npub} (bech32)
 * and canonical 64-character lowercase hex form, using nostr-java's vetted Bech32
 * implementation (no hand-rolled bech32, per the constitution).
 */
@Component
public class PubkeyCodec {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final String NPUB_PREFIX = "npub1";

    /**
     * Normalises an {@code npub} or hex identifier to canonical lowercase hex,
     * validating its format.
     *
     * @throws InvalidPubkeyException if the identifier is null, blank, or malformed
     */
    public String toHex(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new InvalidPubkeyException(String.valueOf(identifier));
        }
        String trimmed = identifier.trim();
        if (trimmed.startsWith(NPUB_PREFIX)) {
            return decodeNpub(trimmed, identifier);
        }
        String hex = trimmed.toLowerCase();
        if (!HEX_64.matcher(hex).matches()) {
            throw new InvalidPubkeyException(identifier);
        }
        return hex;
    }

    /**
     * Encodes a canonical hex public key as a NIP-19 {@code npub}.
     */
    public String toNpub(String hexPubkey) {
        try {
            return Bech32.toBech32(Bech32Prefix.NPUB, hexPubkey);
        } catch (RuntimeException e) {
            throw new InvalidPubkeyException(hexPubkey, e);
        }
    }

    private String decodeNpub(String npub, String original) {
        try {
            String hex = Bech32.fromBech32(npub).toLowerCase();
            if (!HEX_64.matcher(hex).matches()) {
                throw new InvalidPubkeyException(original, "decoded npub is not a 32-byte public key");
            }
            return hex;
        } catch (InvalidPubkeyException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidPubkeyException(original, e);
        }
    }
}
