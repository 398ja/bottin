package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelayController.class)
class RelayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
}
