package xyz.tcheeric.bottin.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for DirectorySettingsClient.
 *
 * <p>Covers the cache window an operator is promised ("takes effect within a
 * minute") and the rule that an unreachable directory degrades to the last
 * known values, or to unconfigured ones, but never to a guess.
 */
class DirectorySettingsClientTest {

    private static final String DIRECTORY_URL = "http://directory.test";
    private static final String SETTINGS_URL = DIRECTORY_URL + "/api/v1/settings";

    private static final String CONFIGURED_JSON = """
            {"blossomUrl":"https://blossom.example",
             "defaultRelays":["ws://relay-a:7777"],
             "discoveryRelays":["wss://relay.damus.io"]}""";

    private static final String CHANGED_JSON = """
            {"blossomUrl":"https://blossom.changed",
             "defaultRelays":[],
             "discoveryRelays":[]}""";

    private MutableClock clock;
    private MockRestServiceServer directory;
    private DirectorySettingsClient settingsClient;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-01T12:00:00Z"));
        RestClient.Builder builder = RestClient.builder().baseUrl(DIRECTORY_URL);
        directory = MockRestServiceServer.bindTo(builder).build();
        settingsClient = new DirectorySettingsClient(builder, clock);
    }

    /**
     * Tests that the configured settings are read from the directory.
     */
    @Test
    void shouldReadSettingsFromTheDirectory() {
        // Given: the directory serves a configured deployment
        directory.expect(requestTo(SETTINGS_URL))
                .andRespond(withSuccess(CONFIGURED_JSON, MediaType.APPLICATION_JSON));

        // When: asking for the current settings
        DirectorySettings settings = settingsClient.current();

        // Then: every field arrives intact
        assertThat(settings.blossomUrl()).isEqualTo("https://blossom.example");
        assertThat(settings.defaultRelays()).containsExactly("ws://relay-a:7777");
        assertThat(settings.discoveryRelays()).containsExactly("wss://relay.damus.io");
        directory.verify();
    }

    /**
     * Tests that a second call inside the cache window is served from memory,
     * so rendering a page does not call the directory every time.
     */
    @Test
    void shouldServeFromCacheWithinTheCacheWindow() {
        // Given: one fetch has happened, and only one request is expected
        directory.expect(requestTo(SETTINGS_URL))
                .andRespond(withSuccess(CONFIGURED_JSON, MediaType.APPLICATION_JSON));
        settingsClient.current();

        // When: asking again just before the window closes
        clock.advance(Duration.ofSeconds(59));
        DirectorySettings settings = settingsClient.current();

        // Then: the cached value is returned without a second request
        assertThat(settings.blossomUrl()).isEqualTo("https://blossom.example");
        directory.verify();
    }

    /**
     * Tests that the directory is asked again once the window closes, which is
     * what makes an administrator's change reach users within a minute.
     */
    @Test
    void shouldRefetchOnceTheCacheWindowHasClosed() {
        // Given: a first fetch, then a changed value waiting at the directory
        directory.expect(requestTo(SETTINGS_URL))
                .andRespond(withSuccess(CONFIGURED_JSON, MediaType.APPLICATION_JSON));
        directory.expect(requestTo(SETTINGS_URL))
                .andRespond(withSuccess(CHANGED_JSON, MediaType.APPLICATION_JSON));
        settingsClient.current();

        // When: asking after the window has closed
        clock.advance(Duration.ofSeconds(61));
        DirectorySettings settings = settingsClient.current();

        // Then: the new value is served
        assertThat(settings.blossomUrl()).isEqualTo("https://blossom.changed");
        directory.verify();
    }

    /**
     * Tests that an unreachable directory does not take the client down with
     * it: the last known settings are served rather than an error.
     */
    @Test
    void shouldServeLastKnownSettingsWhenTheDirectoryFails() {
        // Given: a successful fetch, then a directory that has started failing
        directory.expect(requestTo(SETTINGS_URL))
                .andRespond(withSuccess(CONFIGURED_JSON, MediaType.APPLICATION_JSON));
        directory.expect(requestTo(SETTINGS_URL)).andRespond(withServerError());
        settingsClient.current();

        // When: asking after the window has closed
        clock.advance(Duration.ofSeconds(61));
        DirectorySettings settings = settingsClient.current();

        // Then: the last known values are served
        assertThat(settings.blossomUrl()).isEqualTo("https://blossom.example");
        directory.verify();
    }

    /**
     * Tests that a cold start against an unreachable directory reports nothing
     * configured rather than raising or inventing a fallback relay.
     */
    @Test
    void shouldServeUnconfiguredWhenTheDirectoryFailsAndNothingIsCached() {
        // Given: a directory that fails on the very first call
        directory.expect(requestTo(SETTINGS_URL)).andRespond(withServerError());

        // When: asking for the current settings
        DirectorySettings settings = settingsClient.current();

        // Then: unconfigured, not a guess and not an exception
        assertThat(settings.blossomUrl()).isNull();
        assertThat(settings.defaultRelays()).isEmpty();
        assertThat(settings.discoveryRelays()).isEmpty();
        directory.verify();
    }

    /**
     * A clock the test moves by hand, so the cache window can be crossed
     * without the suite waiting a real minute.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
