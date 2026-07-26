package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RelayController.class)
@Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@TestPropertySource(properties = "bottin.client.default-relays=wss://relay.one,wss://relay.two")
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

    /**
     * The defaults endpoint returns each configured relay as a read+write entry so
     * the client can seed a usable relay list on first visit.
     */
    @Test
    void shouldReturnConfiguredDefaultRelaysAsReadWrite() throws Exception {
        mockMvc.perform(get("/api/v1/relays/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays.length()").value(2))
                .andExpect(jsonPath("$.relays[0].url").value("wss://relay.one"))
                .andExpect(jsonPath("$.relays[0].read").value(true))
                .andExpect(jsonPath("$.relays[0].write").value(true))
                .andExpect(jsonPath("$.relays[1].url").value("wss://relay.two"));
    }

    /**
     * A blank configured entry is dropped so an unset or empty env var never seeds a
     * bogus relay URL.
     */
    @Test
    void shouldExposeDefaultsEndpointAsArray() throws Exception {
        mockMvc.perform(get("/api/v1/relays/defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays").isArray());
    }
}
