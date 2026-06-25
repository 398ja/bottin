package xyz.tcheeric.bottin.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for bottin auto-configuration.
 */
@Data
@ConfigurationProperties(prefix = "bottin")
public class BottinProperties {

    /**
     * Whether bottin auto-configuration is enabled.
     */
    private boolean enabled = true;

    /**
     * Admin dashboard settings.
     */
    private AdminProperties admin = new AdminProperties();

    /**
     * Verification settings.
     */
    private VerificationProperties verification = new VerificationProperties();

    /**
     * External NIP-05 verification settings.
     */
    private ExternalProperties external = new ExternalProperties();

    /**
     * Profile reach (follower count) calculation settings.
     */
    private ReachProperties reach = new ReachProperties();

    @Data
    public static class AdminProperties {
        /**
         * Whether the admin dashboard is enabled.
         */
        private boolean enabled = true;

        /**
         * Default admin username.
         */
        private String defaultUser = "admin";
    }

    @Data
    public static class VerificationProperties {
        /**
         * DNS query timeout in seconds.
         */
        private int dnsTimeoutSeconds = 5;

        /**
         * HTTP verification timeout in seconds.
         */
        private int httpTimeoutSeconds = 10;

        /**
         * Interval for automatic domain re-verification.
         */
        private String recheckInterval = "24h";

        /**
         * How long verification tokens are valid.
         */
        private String tokenValidity = "7d";
    }

    @Data
    public static class ExternalProperties {
        /**
         * Whether external NIP-05 verification endpoint is enabled.
         */
        private boolean enabled = true;

        /**
         * Cache TTL for external verification results in minutes.
         */
        private int cacheTtlMinutes = 5;

        /**
         * Maximum cache entries for external verifications.
         */
        private int cacheMaxSize = 1000;
    }

    @Data
    public static class ReachProperties {
        /**
         * Whether the scheduled reach calculation is enabled.
         */
        private boolean enabled = true;

        /**
         * Cron expression for the recurring reach calculation (default: every 6 hours).
         */
        private String calculationCron = "0 0 */6 * * ?";

        /**
         * Default application relays consulted for every reach calculation.
         */
        private java.util.List<String> defaultRelays = java.util.List.of(
                "wss://relay.damus.io", "wss://nos.lol", "wss://relay.nostr.band");

        /**
         * Per-relay connect/read timeout in seconds.
         */
        private long relayTimeoutSeconds = 12;

        /**
         * Maximum follower events requested per relay.
         */
        private int maxFollowersPerRelay = 5000;

        /**
         * Maximum tracked profiles processed in a single scheduled run.
         */
        private int maxProfilesPerRun = 10000;
    }
}
