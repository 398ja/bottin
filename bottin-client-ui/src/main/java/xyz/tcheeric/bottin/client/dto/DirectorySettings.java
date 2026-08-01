package xyz.tcheeric.bottin.client.dto;

import java.util.List;

/**
 * The deployment settings as served by the bottin directory API.
 *
 * <p>Deserialised straight from {@code GET /api/v1/settings}. Both relay lists
 * are normalised to an empty list rather than null, so no caller has to tell
 * "the deployment configured nothing" apart from "the field was missing".
 *
 * @param blossomUrl      media server the browser uploads images to, null when unconfigured
 * @param defaultRelays   the deployment's system relays
 * @param discoveryRelays relays searched for an imported key's existing profile
 */
public record DirectorySettings(String blossomUrl, List<String> defaultRelays, List<String> discoveryRelays) {

    public DirectorySettings {
        defaultRelays = defaultRelays == null ? List.of() : List.copyOf(defaultRelays);
        discoveryRelays = discoveryRelays == null ? List.of() : List.copyOf(discoveryRelays);
    }

    /**
     * What a client serves when it has never successfully reached the directory.
     * Deliberately not a guess: an unreachable directory yields "nothing is
     * configured", never a fallback relay or media server.
     */
    public static DirectorySettings unconfigured() {
        return new DirectorySettings(null, List.of(), List.of());
    }
}
