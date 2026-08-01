package xyz.tcheeric.bottin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.SettingsData;

import java.util.List;

/**
 * API response carrying the deployment settings a client needs.
 *
 * <p>{@code rateLimitPerMinute} is deliberately absent: the API is its only
 * consumer, and shipping it would invite a second consumer for a value that
 * only means anything inside this process.
 */
@Value
@Builder
@Schema(description = "Admin-maintained deployment settings")
public class SettingsResponse {

    @Schema(description = "Media server (Blossom) the browser uploads profile images to; "
            + "null when the deployment has not configured one",
            example = "https://blossom.example.com")
    String blossomUrl;

    @Schema(description = "The deployment's system relays. Every user's events are published to "
            + "all of these and read back from them, without entering any user's own relay list.",
            example = "[\"ws://relay-a:7777\", \"wss://relay-b.example\"]")
    List<String> defaultRelays;

    @Schema(description = "Relays searched for the already-published profile of a key imported "
            + "from elsewhere",
            example = "[\"wss://relay.damus.io\", \"wss://nos.lol\"]")
    List<String> discoveryRelays;

    public static SettingsResponse from(SettingsData settings) {
        return SettingsResponse.builder()
                .blossomUrl(settings.getBlossomUrl())
                .defaultRelays(settings.getDefaultRelays())
                .discoveryRelays(settings.getDiscoveryRelays())
                .build();
    }
}
