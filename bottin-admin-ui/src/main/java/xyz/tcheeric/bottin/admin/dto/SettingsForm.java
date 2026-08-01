package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.tcheeric.bottin.core.model.SettingsData;

import java.util.Arrays;
import java.util.List;

/**
 * Form DTO for the admin-maintained deployment settings.
 *
 * <p>Relay lists reach the browser as one URL per line, so they are held here
 * as text and translated to lists on the way to the service. Rejecting a bad
 * scheme here gives the operator a field-level error; {@code SettingsService}
 * enforces the same rule again so that no path can store a bad value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsForm {

    /**
     * A whole textarea of relay URLs, one per line. An empty textarea passes:
     * an empty relay list is a configured state, not a missing one.
     */
    private static final String RELAY_LIST_PATTERN = "^\\s*((wss?://\\S+)\\s*)*$";

    private static final String RELAY_SCHEME_MESSAGE = "Each relay must start with ws:// or wss://, one per line";

    private static final String RATE_LIMIT_MESSAGE = "Rate limit must be between 1 and 1000 requests per minute";

    @NotBlank(message = "Media server URL is required")
    @Pattern(regexp = "^https?://\\S+$", message = "Media server URL must start with http:// or https://")
    private String blossomUrl;

    @Pattern(regexp = RELAY_LIST_PATTERN, message = RELAY_SCHEME_MESSAGE)
    private String defaultRelays;

    @Pattern(regexp = RELAY_LIST_PATTERN, message = RELAY_SCHEME_MESSAGE)
    private String discoveryRelays;

    @Min(value = 1, message = RATE_LIMIT_MESSAGE)
    @Max(value = 1000, message = RATE_LIMIT_MESSAGE)
    private int rateLimitPerMinute;

    /**
     * Binds the stored settings onto a form for rendering.
     */
    public static SettingsForm from(SettingsData settings) {
        return SettingsForm.builder()
                .blossomUrl(settings.getBlossomUrl())
                .defaultRelays(asLines(settings.getDefaultRelays()))
                .discoveryRelays(asLines(settings.getDiscoveryRelays()))
                .rateLimitPerMinute(settings.getRateLimitPerMinute())
                .build();
    }

    /**
     * Converts the submitted form into the value object the service stores.
     * Trimming and de-duplication are left to the service, which owns them for
     * every caller rather than only for this form.
     */
    public SettingsData toSettingsData() {
        return SettingsData.builder()
                .blossomUrl(blossomUrl)
                .defaultRelays(asList(defaultRelays))
                .discoveryRelays(asList(discoveryRelays))
                .rateLimitPerMinute(rateLimitPerMinute)
                .build();
    }

    private static String asLines(List<String> relays) {
        return String.join("\n", relays);
    }

    private static List<String> asList(String relays) {
        if (relays == null || relays.isBlank()) {
            return List.of();
        }
        return Arrays.stream(relays.split("\\R"))
                .map(String::trim)
                .filter(relay -> !relay.isEmpty())
                .toList();
    }
}
