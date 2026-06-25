package xyz.tcheeric.bottin.reach;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Bound configuration for profile reach calculation ({@code bottin.reach.*}).
 *
 * <p>Uses {@code @ConfigurationProperties} (not {@code @Value}) so that the
 * {@code default-relays} YAML list binds correctly — a property placeholder
 * cannot resolve an indexed list value.
 */
@Data
@ConfigurationProperties(prefix = "bottin.reach")
public class ReachProperties {

    /** Whether the scheduled reach calculation is enabled. */
    private boolean enabled = true;

    /** Cron expression for the recurring calculation (default: every 6 hours). */
    private String calculationCron = "0 0 */6 * * ?";

    /** Default application relays consulted for every calculation. */
    private List<String> defaultRelays = List.of(
            "wss://relay.damus.io", "wss://nos.lol", "wss://relay.nostr.band");

    /** Per-relay connect/read timeout in seconds. */
    private long relayTimeoutSeconds = 12;

    /** Maximum follower events requested per relay. */
    private int maxFollowersPerRelay = 5000;

    /** Maximum tracked profiles processed in a single scheduled run. */
    private int maxProfilesPerRun = 10000;
}
