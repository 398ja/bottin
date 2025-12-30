package xyz.tcheeric.bottin.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.service.WellKnownJsonGenerator;
import xyz.tcheeric.bottin.web.config.SecurityConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for WellKnownController.
 * Tests the .well-known/nostr.json endpoint behavior per NIP-05 specification.
 */
@WebMvcTest(WellKnownController.class)
@Import(SecurityConfig.class)
class WellKnownControllerTest {

    private static final String WELL_KNOWN_PATH = "/.well-known/nostr.json";
    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_USERNAME = "alice";
    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final String TEST_RELAY = "wss://relay.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WellKnownJsonGenerator wellKnownJsonGenerator;

    /**
     * Tests that requesting a specific username returns only that user's record.
     */
    @Test
    void shouldReturnUserRecordWhenNameParameterProvided() throws Exception {
        // Arrange: Set up mock to return a record for alice
        Map<String, Object> response = buildSingleUserResponse(TEST_USERNAME, TEST_PUBKEY, List.of(TEST_RELAY));
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, TEST_USERNAME))
                .thenReturn(Optional.of(response));

        // Act & Assert: Request with name parameter
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", TEST_USERNAME)
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.names." + TEST_USERNAME, is(TEST_PUBKEY)))
                .andExpect(jsonPath("$.relays." + TEST_PUBKEY, hasSize(1)))
                .andExpect(jsonPath("$.relays." + TEST_PUBKEY + "[0]", is(TEST_RELAY)));
    }

    /**
     * Tests that requesting without name parameter returns all records for the domain.
     */
    @Test
    void shouldReturnAllRecordsWhenNoNameParameter() throws Exception {
        // Arrange: Set up mock to return multiple records
        Map<String, Object> response = buildMultiUserResponse();
        when(wellKnownJsonGenerator.generateForDomain(TEST_DOMAIN)).thenReturn(response);

        // Act & Assert: Request without name parameter
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.names", hasKey("alice")))
                .andExpect(jsonPath("$.names", hasKey("bob")));
    }

    /**
     * Tests that requesting a non-existent username returns empty names object.
     */
    @Test
    void shouldReturnEmptyNamesWhenUsernameNotFound() throws Exception {
        // Arrange: Set up mock to return empty for unknown user
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, "unknown"))
                .thenReturn(Optional.empty());

        // Act & Assert: Request with non-existent name
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", "unknown")
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.names").isEmpty());
    }

    /**
     * Tests that the response includes proper CORS headers.
     */
    @Test
    void shouldIncludeCorsHeaders() throws Exception {
        // Arrange: Set up mock to return a record
        Map<String, Object> response = buildSingleUserResponse(TEST_USERNAME, TEST_PUBKEY, List.of());
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, TEST_USERNAME))
                .thenReturn(Optional.of(response));

        // Act & Assert: Check CORS header
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", TEST_USERNAME)
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    /**
     * Tests that the response includes cache control headers.
     */
    @Test
    void shouldIncludeCacheControlHeaders() throws Exception {
        // Arrange: Set up mock to return a record
        Map<String, Object> response = buildSingleUserResponse(TEST_USERNAME, TEST_PUBKEY, List.of());
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, TEST_USERNAME))
                .thenReturn(Optional.of(response));

        // Act & Assert: Check cache headers
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", TEST_USERNAME)
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"));
    }

    /**
     * Tests that the domain is extracted from Host header (removing port if present).
     */
    @Test
    void shouldExtractDomainFromHostWithPort() throws Exception {
        // Arrange: Set up mock to return a record
        Map<String, Object> response = buildSingleUserResponse(TEST_USERNAME, TEST_PUBKEY, List.of());
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, TEST_USERNAME))
                .thenReturn(Optional.of(response));

        // Act & Assert: Request with port in Host header
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", TEST_USERNAME)
                        .header("Host", TEST_DOMAIN + ":8080"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names." + TEST_USERNAME, is(TEST_PUBKEY)));
    }

    /**
     * Tests that usernames are case-insensitive.
     */
    @Test
    void shouldHandleCaseInsensitiveUsername() throws Exception {
        // Arrange: Set up mock to return a record for uppercase alice
        Map<String, Object> response = buildSingleUserResponse(TEST_USERNAME, TEST_PUBKEY, List.of());
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, "ALICE"))
                .thenReturn(Optional.of(response));

        // Act & Assert: Request with uppercase name
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", "ALICE")
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names." + TEST_USERNAME, is(TEST_PUBKEY)));
    }

    /**
     * Tests that relays are omitted from response when empty.
     */
    @Test
    void shouldOmitRelaysWhenEmpty() throws Exception {
        // Arrange: Set up mock to return record without relays
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("names", Map.of(TEST_USERNAME, TEST_PUBKEY));
        when(wellKnownJsonGenerator.generateForUsername(TEST_DOMAIN, TEST_USERNAME))
                .thenReturn(Optional.of(response));

        // Act & Assert: Check relays not present
        mockMvc.perform(get(WELL_KNOWN_PATH)
                        .param("name", TEST_USERNAME)
                        .header("Host", TEST_DOMAIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relays").doesNotExist());
    }

    private Map<String, Object> buildSingleUserResponse(String username, String pubkey, List<String> relays) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("names", Map.of(username, pubkey));
        if (!relays.isEmpty()) {
            response.put("relays", Map.of(pubkey, relays));
        }
        return response;
    }

    private Map<String, Object> buildMultiUserResponse() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("alice", TEST_PUBKEY);
        names.put("bob", "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("names", names);
        return response;
    }
}
