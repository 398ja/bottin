package xyz.tcheeric.bottin.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import xyz.tcheeric.bottin.client.config.ClientProperties;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reads the deployment settings from the bottin directory API.
 *
 * <p>This module holds no database dependency, so the settings an administrator
 * maintains are fetched over HTTP with the same credentials
 * {@link DirectoryRegistrationService} uses. The browser never makes this call:
 * the directory URL is an internal address it cannot resolve, and the
 * credentials must not leave this server.
 */
@Service
@Slf4j
public class DirectorySettingsClient {

    /**
     * How long a fetched value is served before the directory is asked again.
     * The admin settings page tells operators a change takes effect within a
     * minute, so this window and that promise must stay in step.
     */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final Clock clock;

    private volatile DirectorySettings cached;
    private volatile Instant cachedAt;

    @Autowired
    public DirectorySettingsClient(ClientProperties clientProperties) {
        this(RestClient.builder()
                        .baseUrl(clientProperties.getDirectoryUrl())
                        .defaultHeaders(headers -> headers.setBasicAuth(
                                clientProperties.getDirectoryUsername(),
                                clientProperties.getDirectoryPassword())),
                Clock.systemUTC());
    }

    DirectorySettingsClient(RestClient.Builder restClientBuilder, Clock clock) {
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    /**
     * The current settings, from cache when fresh and from the directory
     * otherwise.
     *
     * <p>Never raises and never guesses. An unreachable directory yields the
     * last known values, or unconfigured ones when there are none.
     */
    public DirectorySettings current() {
        if (isCacheFresh()) {
            return cached;
        }

        try {
            DirectorySettings fetched = restClient.get()
                    .uri("/api/v1/settings")
                    .retrieve()
                    .body(DirectorySettings.class);

            if (fetched == null) {
                return lastKnownOrUnconfigured("empty_response");
            }

            cached = fetched;
            cachedAt = clock.instant();
            return fetched;
        } catch (Exception e) {
            return lastKnownOrUnconfigured(e.getMessage());
        }
    }

    private boolean isCacheFresh() {
        return cached != null
                && Duration.between(cachedAt, clock.instant()).compareTo(CACHE_TTL) < 0;
    }

    /**
     * The stale value is served without refreshing its timestamp, so the next
     * call retries the directory rather than waiting out another window.
     */
    private DirectorySettings lastKnownOrUnconfigured(String reason) {
        if (cached != null) {
            log.warn("directory_settings_fetch_failed fallback=last_known reason={}", reason);
            return cached;
        }
        log.warn("directory_settings_fetch_failed fallback=unconfigured reason={}", reason);
        return DirectorySettings.unconfigured();
    }
}
