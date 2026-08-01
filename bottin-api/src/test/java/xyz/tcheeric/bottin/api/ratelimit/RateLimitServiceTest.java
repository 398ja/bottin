package xyz.tcheeric.bottin.api.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.service.SettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RateLimitService.
 *
 * <p>The limit is read from the admin-maintained settings rather than from a
 * startup property, so these tests cover both that it is honoured and that
 * changing it takes effect without restarting the API.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @Mock
    private SettingsService settingsService;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(settingsService);
    }

    /**
     * Tests that requests within the configured allowance are served.
     */
    @Test
    void shouldAllowRequestsUpToTheConfiguredLimit() {
        // Given: an allowance of two requests per minute
        when(settingsService.find()).thenReturn(settingsWithLimit(2));

        // When & Then: both are served
        assertThat(rateLimitService.isAllowed(CLIENT_IP)).isTrue();
        assertThat(rateLimitService.isAllowed(CLIENT_IP)).isTrue();
    }

    /**
     * Tests that the request after the allowance is refused, which is the whole
     * point of the limit.
     */
    @Test
    void shouldRejectRequestsBeyondTheConfiguredLimit() {
        // Given: an allowance of one request per minute, already spent
        when(settingsService.find()).thenReturn(settingsWithLimit(1));
        rateLimitService.isAllowed(CLIENT_IP);

        // When & Then: the next request is refused
        assertThat(rateLimitService.isAllowed(CLIENT_IP)).isFalse();
    }

    /**
     * Tests that a client that has issued no requests is told it has the whole
     * configured allowance, not a hardcoded one.
     */
    @Test
    void shouldReportTheFullAllowanceWhenClientIsUnseen() {
        // Given: an allowance of 45 requests per minute
        when(settingsService.find()).thenReturn(settingsWithLimit(45));

        // When: asking what an unseen client has left
        int remaining = rateLimitService.getRemainingRequests(CLIENT_IP);

        // Then: the configured allowance is reported in full
        assertThat(remaining).isEqualTo(45);
    }

    /**
     * Tests that each client address is tracked separately, so one caller
     * exhausting its allowance does not refuse everybody else.
     */
    @Test
    void shouldTrackEachClientAddressSeparately() {
        // Given: an allowance of one request, spent by the first client
        when(settingsService.find()).thenReturn(settingsWithLimit(1));
        rateLimitService.isAllowed(CLIENT_IP);

        // When & Then: a different client still has its own allowance
        assertThat(rateLimitService.isAllowed("198.51.100.4")).isTrue();
    }

    /**
     * Tests that an administrator lowering the limit takes effect on the very
     * next request, which is the reason it moved out of a startup property.
     */
    @Test
    void shouldApplyAChangedLimitWithoutRestart() {
        // Given: an allowance of two, lowered to one before the second request
        when(settingsService.find()).thenReturn(settingsWithLimit(2), settingsWithLimit(1));

        // When: the first request is served under the original allowance
        assertThat(rateLimitService.isAllowed(CLIENT_IP)).isTrue();

        // Then: the second is refused under the lowered one, with no restart
        assertThat(rateLimitService.isAllowed(CLIENT_IP)).isFalse();
    }

    private SettingsData settingsWithLimit(int rateLimitPerMinute) {
        return SettingsData.builder()
                .rateLimitPerMinute(rateLimitPerMinute)
                .build();
    }
}
