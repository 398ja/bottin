package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelayController.class)
class RelayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DirectorySettingsClient settingsClient;

    @Test
    void shouldListRelays() throws Exception {
        mockMvc.perform(get("/api/v1/relays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays").isArray())
                .andExpect(jsonPath("$.relays").isEmpty());
    }

    @Test
    void shouldAddValidRelay() throws Exception {
        mockMvc.perform(post("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "wss://relay.example.com",
                                "read", true,
                                "write", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("added"))
                .andExpect(jsonPath("$.url").value("wss://relay.example.com"));
    }

    @Test
    void shouldRejectNullUrl() throws Exception {
        mockMvc.perform(post("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "read", true,
                                "write", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_url"));
    }

    @Test
    void shouldRejectNonWssUrl() throws Exception {
        mockMvc.perform(post("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "http://relay.example.com",
                                "read", true,
                                "write", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scheme"));
    }

    @Test
    void shouldRejectWsUrl() throws Exception {
        mockMvc.perform(post("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "ws://relay.example.com",
                                "read", true,
                                "write", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scheme"));
    }

    @Test
    void shouldRejectEmptyUrl() throws Exception {
        mockMvc.perform(post("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "",
                                "read", true,
                                "write", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_url"));
    }

    @Test
    void shouldUpdateRelayPermissions() throws Exception {
        mockMvc.perform(put("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "wss://relay.example.com",
                                "read", false,
                                "write", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"));
    }

    @Test
    void shouldRemoveRelay() throws Exception {
        mockMvc.perform(delete("/api/v1/relays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "wss://relay.example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("removed"));
    }

    @Test
    void shouldPublishRelayList() throws Exception {
        mockMvc.perform(post("/api/v1/relays/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.published_to").isArray());
    }

    /**
     * The system relays are the deployment's own, applied to every user's publishes
     * and reads. They are served as plain URLs because they never enter a user's own
     * relay list, where per-relay read/write flags would be the user's to set.
     */
    @Test
    void shouldServeConfiguredSystemRelaysAsPlainUrls() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(new DirectorySettings(
                null, List.of("ws://relay-a:7777", "wss://relay-b.example"), List.of()));

        // Act & Assert
        mockMvc.perform(get("/api/v1/relays/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays.length()").value(2))
                .andExpect(jsonPath("$.relays[0]").value("ws://relay-a:7777"))
                .andExpect(jsonPath("$.relays[1]").value("wss://relay-b.example"));
    }

    /**
     * A deployment with no system relays yields an empty array rather than null, so
     * the browser always has a well-formed list to union with the user's own.
     */
    @Test
    void shouldServeEmptyArrayWhenNoSystemRelaysConfigured() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(DirectorySettings.unconfigured());

        // Act & Assert
        mockMvc.perform(get("/api/v1/relays/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays").isArray())
                .andExpect(jsonPath("$.relays").isEmpty());
    }
}
