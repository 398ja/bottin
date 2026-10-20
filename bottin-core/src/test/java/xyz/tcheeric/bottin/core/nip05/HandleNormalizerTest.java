package xyz.tcheeric.bottin.core.nip05;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class HandleNormalizerTest {

    // Leading and trailing whitespace is removed and the handle is lowercased; nothing else changes.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' alice'|alice",
            "'alice '|alice",
            "Alice|alice",
            "' ALICE '|alice",
            "'al ice'|al ice",
            "alice!|alice!"
    })
    void shouldTrimAndLowercaseHandle(String raw, String expected) {
        // Act
        String normalized = HandleNormalizer.normalizeHandle(raw);

        // Assert
        assertThat(normalized).isEqualTo(expected);
    }

    // A missing value stays missing, so bean validation reports it instead of a NullPointerException.
    @Test
    void shouldPassNullThrough() {
        assertThat(HandleNormalizer.normalizeHandle(null)).isNull();
        assertThat(HandleNormalizer.normalizeNip05(null)).isNull();
    }

    // Lowercasing ignores the JVM's default locale: under Turkish, "I" must still become "i".
    @Test
    void shouldLowercaseIndependentOfDefaultLocale() {
        // Arrange
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            // Act and assert
            assertThat(HandleNormalizer.normalizeHandle("ALICE")).isEqualTo("alice");
        } finally {
            Locale.setDefault(previous);
        }
    }

    // Both halves of name@domain are trimmed and lowercased.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'Alice@Domain '|alice@domain",
            "' alice @ Example.COM '|alice@example.com",
            "no-at-sign|no-at-sign"
    })
    void shouldNormaliseBothPartsOfNip05(String raw, String expected) {
        assertThat(HandleNormalizer.normalizeNip05(raw)).isEqualTo(expected);
    }
}
