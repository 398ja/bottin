package xyz.tcheeric.bottin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NostrPublicKeys.
 *
 * <p>This converter decides whether two spellings of a key are one
 * administrator or two. Getting it wrong in the permissive direction admits
 * somebody; getting it wrong in the strict direction locks out a legitimate
 * administrator with a valid key. Both are covered.
 */
class NostrPublicKeysTest {

    /** A known keypair: the npub and hex below are the same key. */
    private static final String NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    /**
     * Tests that a bech32 npub is decoded to the hex form the deployment stores
     * and compares.
     */
    @Test
    void shouldDecodeNpubToCanonicalHex() {
        // When: an npub is canonicalised
        Optional<String> canonical = NostrPublicKeys.toCanonicalHex(NPUB);

        // Then: it yields the matching hex
        assertThat(canonical).contains(HEX);
    }

    /**
     * Tests that hex input is accepted unchanged, so an operator who has the hex
     * form is not forced to convert it.
     */
    @Test
    void shouldAcceptHexUnchanged() {
        assertThat(NostrPublicKeys.toCanonicalHex(HEX)).contains(HEX);
    }

    /**
     * Tests that the npub and hex spellings of one key produce one value. This
     * is what makes "the same key entered either way is one administrator" true.
     */
    @Test
    void shouldTreatNpubAndHexOfOneKeyAsTheSameValue() {
        assertThat(NostrPublicKeys.toCanonicalHex(NPUB))
                .isEqualTo(NostrPublicKeys.toCanonicalHex(HEX));
    }

    /**
     * Tests that uppercase hex is lowered, so case alone cannot create a second
     * entry for a key already present.
     */
    @Test
    void shouldLowercaseUppercaseHex() {
        assertThat(NostrPublicKeys.toCanonicalHex(HEX.toUpperCase())).contains(HEX);
    }

    /**
     * Tests that surrounding whitespace is tolerated, since a pasted key
     * routinely carries it.
     */
    @Test
    void shouldTrimSurroundingWhitespace() {
        assertThat(NostrPublicKeys.toCanonicalHex("  " + HEX + "\n")).contains(HEX);
    }

    /**
     * Tests that an npub whose checksum does not verify is rejected. A corrupted
     * key that decoded anyway would admit a key nobody holds.
     */
    @Test
    void shouldRejectAnNpubWithABrokenChecksum() {
        String corrupted = NPUB.substring(0, NPUB.length() - 1) + "q";

        assertThat(NostrPublicKeys.toCanonicalHex(corrupted)).isEmpty();
    }

    /**
     * Tests that hex of the wrong length is rejected. A 63- or 65-character
     * value is a typo, not a key.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459",
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459dd"
    })
    void shouldRejectHexOfTheWrongLength(String wrongLength) {
        assertThat(NostrPublicKeys.toCanonicalHex(wrongLength)).isEmpty();
    }

    /**
     * Tests that a 64-character string containing non-hex characters is
     * rejected, since length alone does not make something a key.
     */
    @Test
    void shouldRejectSixtyFourNonHexCharacters() {
        assertThat(NostrPublicKeys.toCanonicalHex("z".repeat(64))).isEmpty();
    }

    /**
     * Tests that ordinary rubbish is rejected rather than stored.
     */
    @Test
    void shouldRejectAValueThatIsNotAKey() {
        assertThat(NostrPublicKeys.toCanonicalHex("not-a-key")).isEmpty();
    }

    /**
     * Tests the boundary inputs, which reach this converter whenever a form is
     * submitted empty.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankInput(String blank) {
        assertThat(NostrPublicKeys.toCanonicalHex(blank)).isEmpty();
    }

    /**
     * Tests that null is answered with an empty result rather than an exception
     * or a null return (Principle VIII).
     */
    @Test
    void shouldRejectNullWithoutThrowing() {
        assertThat(NostrPublicKeys.toCanonicalHex(null)).isEmpty();
    }
}
