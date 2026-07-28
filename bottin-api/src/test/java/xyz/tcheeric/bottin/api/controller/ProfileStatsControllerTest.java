package xyz.tcheeric.bottin.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.core.exception.InvalidPubkeyException;
import xyz.tcheeric.bottin.core.reach.ProfileReach;
import xyz.tcheeric.bottin.core.reach.ReachQueryService;
import xyz.tcheeric.bottin.api.config.SecurityConfig;
import xyz.tcheeric.bottin.api.ratelimit.RateLimitService;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for the public, rate-limited profile reach endpoint.
 * Verifies the acceptance scenarios for User Story 1 (lookup) at the HTTP layer.
 */
@WebMvcTest(ProfileStatsController.class)
@Import(SecurityConfig.class)
class ProfileStatsControllerTest {

    private static final String HEX = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2";
    private static final String PATH = "/api/v1/profiles/" + HEX + "/reach";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReachQueryService reachQueryService;

    @MockBean
    private RateLimitService rateLimitService;

    /** A profile with a stored figure returns 200 with the count, completeness, and npub. */
    @Test
    void shouldReturnReachWhenAvailable() throws Exception {
        // Arrange
        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(reachQueryService.findReach(HEX)).thenReturn(Optional.of(
                new ProfileReach(HEX, "npub1example", 1542L, true, Instant.parse("2026-06-25T06:00:12Z"))));

        // Act / Assert
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pubkey").value(HEX))
                .andExpect(jsonPath("$.npub").value("npub1example"))
                .andExpect(jsonPath("$.reachCount").value(1542))
                .andExpect(jsonPath("$.complete").value(true));
    }

    /** A profile that has never been calculated returns 404 REACH_NOT_AVAILABLE (not a zero figure). */
    @Test
    void shouldReturnNotFoundWhenReachUnavailable() throws Exception {
        // Arrange
        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(reachQueryService.findReach(HEX)).thenReturn(Optional.empty());

        // Act / Assert
        mockMvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REACH_NOT_AVAILABLE"));
    }

    /** A malformed identifier is rejected with 400 before any lookup result is returned. */
    @Test
    void shouldReturnBadRequestWhenIdentifierInvalid() throws Exception {
        // Arrange
        when(rateLimitService.isAllowed(anyString())).thenReturn(true);
        when(reachQueryService.findReach(anyString())).thenThrow(new InvalidPubkeyException("not-an-npub"));

        // Act / Assert
        mockMvc.perform(get("/api/v1/profiles/not-an-npub/reach"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PUBKEY"));
    }

    /** A client over the rate limit receives 429 and no lookup is attempted. */
    @Test
    void shouldReturnTooManyRequestsWhenRateLimited() throws Exception {
        // Arrange
        when(rateLimitService.isAllowed(anyString())).thenReturn(false);

        // Act / Assert
        mockMvc.perform(get(PATH))
                .andExpect(status().isTooManyRequests());
    }
}
