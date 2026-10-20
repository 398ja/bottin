package xyz.tcheeric.bottin.core.nip05;

import lombok.experimental.UtilityClass;

import java.util.Locale;

/**
 * The one place a handle, a domain or a {@code name@domain} identifier is put into its
 * canonical form: surrounding whitespace removed, lowercased with {@link Locale#ROOT}.
 *
 * <p>Handles are always stored lowercase. Request DTOs apply this while they are deserialised,
 * so bean validation checks the canonical value: {@code "alice "} from a mobile keyboard is the
 * handle {@code alice}, not a 400 (imani-wallet#342). It never repairs a handle that is invalid
 * for another reason: the {@code @Pattern} still refuses an inner space or a forbidden
 * character on the normalised value.
 *
 * <p>{@code null} passes through so that {@code @NotBlank} reports a missing value rather than
 * this throwing.
 */
@UtilityClass
public class HandleNormalizer {

    private static final char SEPARATOR = '@';

    /**
     * Canonical form of a handle, the name part of a NIP-05 identifier.
     *
     * @param raw the handle as received, may be {@code null}
     * @return the stripped, lowercased handle, or {@code null}
     */
    public static String normalizeHandle(String raw) {
        return raw == null ? null : raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Canonical form of a domain. The same rule as a handle, under a name that says which part
     * the caller holds.
     *
     * @param raw the domain as received, may be {@code null}
     * @return the stripped, lowercased domain, or {@code null}
     */
    public static String normalizeDomain(String raw) {
        return normalizeHandle(raw);
    }

    /**
     * Canonical form of a {@code name@domain} identifier, each part normalised on its own, so
     * {@code " Alice @ Example.COM "} becomes {@code alice@example.com}. A value without an
     * {@code @} is normalised as a whole and left for the caller's format check to refuse.
     *
     * @param raw the identifier as received, may be {@code null}
     * @return the canonical identifier, or {@code null}
     */
    public static String normalizeNip05(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf(SEPARATOR);
        if (separator < 0) {
            return normalizeHandle(raw);
        }
        return normalizeHandle(raw.substring(0, separator))
                + SEPARATOR
                + normalizeDomain(raw.substring(separator + 1));
    }
}
