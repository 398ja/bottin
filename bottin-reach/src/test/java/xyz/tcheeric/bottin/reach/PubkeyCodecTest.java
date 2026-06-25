package xyz.tcheeric.bottin.reach;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.bottin.core.exception.InvalidPubkeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for npub/hex conversion and validation in {@link PubkeyCodec}.
 */
class PubkeyCodecTest {

    private static final String HEX = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2";

    private final PubkeyCodec codec = new PubkeyCodec();

    /** A valid 64-char hex identifier is returned normalised to lowercase. */
    @Test
    void shouldAcceptHexIdentifier() {
        // Given / When
        String result = codec.toHex(HEX.toUpperCase());

        // Then
        assertThat(result).isEqualTo(HEX);
    }

    /** Encoding a hex key to npub and decoding it back yields the original hex. */
    @Test
    void shouldRoundTripHexThroughNpub() {
        // Given: a hex pubkey encoded to npub
        String npub = codec.toNpub(HEX);

        // When: decoding the npub back to hex
        String roundTripped = codec.toHex(npub);

        // Then: the npub is well-formed and the hex is preserved
        assertThat(npub).startsWith("npub1");
        assertThat(roundTripped).isEqualTo(HEX);
    }

    /** A malformed (too short) hex identifier is rejected. */
    @Test
    void shouldRejectMalformedHex() {
        // Given: a too-short hex string
        String invalid = "abc123";

        // When / Then
        assertThatThrownBy(() -> codec.toHex(invalid))
                .isInstanceOf(InvalidPubkeyException.class);
    }

    /** A string with the npub prefix that is not valid bech32 is rejected. */
    @Test
    void shouldRejectInvalidNpub() {
        // Given: an npub-prefixed but invalid value
        String invalid = "npub1notvalidbech32";

        // When / Then
        assertThatThrownBy(() -> codec.toHex(invalid))
                .isInstanceOf(InvalidPubkeyException.class);
    }

    /** A null identifier is rejected rather than throwing NPE. */
    @Test
    void shouldRejectNullIdentifier() {
        // When / Then
        assertThatThrownBy(() -> codec.toHex(null))
                .isInstanceOf(InvalidPubkeyException.class);
    }
}
