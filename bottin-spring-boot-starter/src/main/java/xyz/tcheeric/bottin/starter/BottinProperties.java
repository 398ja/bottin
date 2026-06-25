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
}
