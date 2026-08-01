package xyz.tcheeric.bottin.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.api.config.SecurityConfig;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.service.SettingsService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for SettingsController.
 *
 * <p>The client server is this endpoint's only consumer, so the tests pin the
 * payload shape it depends on, including what is deliberately absent from it.
 */
@WebMvcTest(SettingsController.class)
@Import(SecurityConfig.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsService settingsService;

    /**
     * Tests that a configured deployment's media server and both relay lists are
     * served, which is everything the client needs to render and publish.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldServeMediaServerAndBothRelayListsWhenConfigured() throws Exception {
        // Arrange
        when(settingsService.find()).thenReturn(configuredSettings());

        // Act & Assert
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blossomUrl").value("https://blossom.example"))
                .andExpect(jsonPath("$.defaultRelays[0]").value("ws://relay-a:7777"))
                .andExpect(jsonPath("$.discoveryRelays[0]").value("wss://relay.damus.io"));
    }

    /**
     * Tests that the rate limit is not published. The API is its only consumer,
     * and exposing it would invite a second one.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldOmitTheRateLimitFromThePayload() throws Exception {
        // Arrange
        when(settingsService.find()).thenReturn(configuredSettings());

        // Act & Assert
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitPerMinute").doesNotExist());
    }

    /**
     * Tests that an unconfigured media server is served as null rather than
     * omitted or guessed, so the client can disable uploads for a stated reason.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldServeNullMediaServerWhenUnconfigured() throws Exception {
        // Arrange
        when(settingsService.find()).thenReturn(unconfiguredSettings());

        // Act & Assert
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blossomUrl").doesNotExist());
    }

    /**
     * Tests that unconfigured relay lists are served as empty arrays, so the
     * client never has to distinguish absent from empty.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldServeEmptyArraysWhenNoRelaysConfigured() throws Exception {
        // Arrange
        when(settingsService.find()).thenReturn(unconfiguredSettings());

        // Act & Assert
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultRelays").isArray())
                .andExpect(jsonPath("$.defaultRelays").isEmpty())
                .andExpect(jsonPath("$.discoveryRelays").isArray())
                .andExpect(jsonPath("$.discoveryRelays").isEmpty());
    }

    /**
     * Tests that the deployment's relay and media topology is not public. It is
     * not secret, but it is not the public's business either.
     */
    @Test
    void shouldRejectCallersWithoutTheApiRole() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized());
    }

    private SettingsData configuredSettings() {
        return SettingsData.builder()
                .blossomUrl("https://blossom.example")
                .defaultRelays(List.of("ws://relay-a:7777"))
                .discoveryRelays(List.of("wss://relay.damus.io"))
                .rateLimitPerMinute(30)
                .updatedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .build();
    }

    private SettingsData unconfiguredSettings() {
        return SettingsData.builder()
                .rateLimitPerMinute(30)
                .updatedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .build();
    }
}
