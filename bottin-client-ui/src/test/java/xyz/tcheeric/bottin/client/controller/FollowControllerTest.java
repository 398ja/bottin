package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FollowController.class)
class FollowControllerTest {

    private static final String VALID_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldFollowValidPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/follow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", VALID_PUBKEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("followed"))
                .andExpect(jsonPath("$.pubkey").value(VALID_PUBKEY));
    }

    @Test
    void shouldReturnBadRequestForMissingPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/follow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForInvalidPubkeyFormat() throws Exception {
        mockMvc.perform(post("/api/v1/follow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_pubkey"));
    }

    @Test
    void shouldReturnBadRequestForShortPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/follow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_pubkey"));
    }

    @Test
    void shouldUnfollowPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/unfollow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", VALID_PUBKEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unfollowed"));
    }

    @Test
    void shouldListFollows() throws Exception {
        mockMvc.perform(get("/api/v1/follows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.follows").isArray())
                .andExpect(jsonPath("$.follows").isEmpty());
    }
}
