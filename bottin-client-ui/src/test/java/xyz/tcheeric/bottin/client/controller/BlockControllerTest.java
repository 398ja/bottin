package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BlockController.class)
class BlockControllerTest {

    private static final String VALID_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldBlockValidPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/block").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", VALID_PUBKEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.pubkey").value(VALID_PUBKEY));
    }

    @Test
    void shouldReturnBadRequestForMissingPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/block").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForInvalidPubkeyFormat() throws Exception {
        mockMvc.perform(post("/api/v1/block").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_pubkey"));
    }

    @Test
    void shouldUnblockPubkey() throws Exception {
        mockMvc.perform(post("/api/v1/unblock").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("pubkey", VALID_PUBKEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unblocked"));
    }

    @Test
    void shouldListBlocks() throws Exception {
        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.blocks").isEmpty());
    }
}
