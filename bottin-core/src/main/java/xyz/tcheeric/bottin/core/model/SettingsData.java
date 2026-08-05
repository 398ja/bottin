package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Immutable value object holding the deployment configuration an administrator
 * maintains, as opposed to the bootstrap and infrastructure settings that stay
 * in the environment.
 *
 * <p>There is exactly one of these per deployment. Both relay lists default to
 * empty rather than null, so callers never branch on absence: an unconfigured
 * deployment holds an empty list, which is a value.
 */
@Value
@Builder(toBuilder = true)
public class SettingsData {

    /**
     * Media server (Blossom) the browser uploads profile images to.
     * Null between first boot and the administrator's first save.
     */
    String blossomUrl;

    /**
     * The deployment's system relays. Every one of them is both published to and
     * searched, for every user, and none of them enters a user's own relay list.
     *
     * <p>Named for the {@code default_relays_json} column it maps to; the admin
     * UI and documentation call these the <em>system relays</em>, because they
     * are applied on every publish rather than copied once as a starting point.
     */
    @Builder.Default
    List<String> defaultRelays = List.of();

    /**
     * Relays searched for the already-published profile of a key imported from
     * elsewhere.
     */
    @Builder.Default
    List<String> discoveryRelays = List.of();

    /**
     * Requests per minute allowed per client on rate-limited public endpoints.
     */
    int rateLimitPerMinute;

    /**
     * When an administrator last saved these settings.
     */
    Instant updatedAt;
}
