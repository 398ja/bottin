package xyz.tcheeric.bottin.service;

import lombok.extern.slf4j.Slf4j;
import nostr.crypto.bech32.Bech32;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Converts a Nostr public key written either way into the one form the
 * deployment stores and compares.
 *
 * <p>A key can be written as {@code npub1…} (NIP-19 bech32) or as 64 hexadecimal
 * characters (NIP-01). They are the same key, and an administrator who entered
 * one form and later the other must be recognised as one administrator rather
 * than two. Everything downstream — storage, uniqueness, session revocation —
 * uses the canonical lowercase hex this produces.
 *
 * <p>Bech32 decoding is nostr-java's. Principle VI forbids hand-rolling it, and
 * a bech32 implementation that is subtly wrong fails by accepting a corrupted
 * key rather than by refusing a good one.
 */
@Slf4j
public final class NostrPublicKeys {

    private static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");

    private static final String NPUB_PREFIX = "npub1";

    private NostrPublicKeys() {
    }

    /**
     * The canonical lowercase hex form of a public key, or empty when the value
     * is not a public key at all.
     *
     * <p>Returns an empty {@code Optional} rather than null so a caller cannot
     * accidentally store the absence (Principle VIII), and never throws: a
     * malformed key is an ordinary outcome of a person filling in a form, not an
     * exceptional condition.
     */
    public static Optional<String> toCanonicalHex(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String candidate = key.trim().toLowerCase(Locale.ROOT);

        if (candidate.startsWith(NPUB_PREFIX)) {
            return decodeBech32(candidate);
        }

        return HEX_64.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }

    /**
     * Whether two written forms denote the same key. Neither needs to be
     * canonical, and a value that is not a key equals nothing, including itself.
     */
    public static boolean areSameKey(String one, String other) {
        Optional<String> first = toCanonicalHex(one);
        return first.isPresent() && first.equals(toCanonicalHex(other));
    }

    /**
     * A bech32 decode that fails quietly. The library throws on a bad checksum,
     * which is the expected answer for a mistyped key rather than a fault.
     *
     * <p>Catching {@code Exception} is broader than Principle VI would like, but
     * {@code Bech32.fromBech32} declares {@code throws Exception} rather than a
     * decoding-specific type, so there is nothing narrower to name. The scope is
     * one library call whose only outcome of interest is "this did not decode".
     */
    private static Optional<String> decodeBech32(String npub) {
        try {
            String decoded = Bech32.fromBech32(npub).toLowerCase(Locale.ROOT);
            return HEX_64.matcher(decoded).matches() ? Optional.of(decoded) : Optional.empty();
        } catch (Exception e) {
            log.debug("nostr_pubkey_decode_failed reason=invalid_bech32 error={}", e.getMessage());
            return Optional.empty();
        }
    }
}
