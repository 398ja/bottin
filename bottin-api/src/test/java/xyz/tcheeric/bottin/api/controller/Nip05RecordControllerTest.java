package xyz.tcheeric.bottin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordExistsException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordNotFoundException;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.service.Nip05RecordService;
import xyz.tcheeric.bottin.api.config.SecurityConfig;
import xyz.tcheeric.bottin.api.dto.CreateNip05RecordRequest;
import xyz.tcheeric.bottin.api.dto.UpdateNip05RecordRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for Nip05RecordController.
 * Tests REST API endpoints for NIP-05 record management.
 */
@WebMvcTest(Nip05RecordController.class)
@Import(SecurityConfig.class)
class Nip05RecordControllerTest {

    private static final String API_BASE = "/api/v1/records";
    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private Nip05RecordService nip05RecordService;

    /**
     * Tests that listing records returns a paginated response.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnPaginatedRecordsWhenListingAll() throws Exception {
        // Arrange: Create test data
        Nip05RecordData record = createTestRecord(1L, "alice", "example.com");
        when(nip05RecordService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record)));

        // Act & Assert
        mockMvc.perform(get(API_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username", is("alice")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    /**
     * Tests that getting a record by ID returns the record.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnRecordWhenFoundById() throws Exception {
        // Arrange
        Nip05RecordData record = createTestRecord(1L, "alice", "example.com");
        when(nip05RecordService.findById(1L)).thenReturn(Optional.of(record));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.domain", is("example.com")));
    }

    /**
     * Tests that 404 is returned when record not found by ID.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn404WhenRecordNotFoundById() throws Exception {
        // Arrange
        when(nip05RecordService.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests creating a new record successfully.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldCreateRecordSuccessfully() throws Exception {
        // Arrange
        CreateNip05RecordRequest request = CreateNip05RecordRequest.builder()
                .username("alice")
                .domain("example.com")
                .pubkey(TEST_PUBKEY)
                .relays(List.of("wss://relay.example.com"))
                .build();

        Nip05RecordData created = createTestRecord(1L, "alice", "example.com");
        when(nip05RecordService.create(eq("alice"), eq("example.com"), eq(TEST_PUBKEY), any()))
                .thenReturn(created);

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.nip05", is("alice@example.com")));
    }

    /**
     * Tests that validation errors are returned for invalid create request.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnValidationErrorForInvalidCreateRequest() throws Exception {
        // Arrange: Create request with invalid pubkey
        CreateNip05RecordRequest request = CreateNip05RecordRequest.builder()
                .username("alice")
                .domain("example.com")
                .pubkey("invalid-pubkey")
                .build();

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    /**
     * Tests that 409 is returned when record already exists.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn409WhenRecordAlreadyExists() throws Exception {
        // Arrange
        CreateNip05RecordRequest request = CreateNip05RecordRequest.builder()
                .username("alice")
                .domain("example.com")
                .pubkey(TEST_PUBKEY)
                .build();

        when(nip05RecordService.create(eq("alice"), eq("example.com"), eq(TEST_PUBKEY), any()))
                .thenThrow(new Nip05RecordExistsException("alice@example.com"));

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    /**
     * imani-wallet#342: a padded, mixed-case handle is created as its canonical lowercase form
     * rather than refused by the username pattern.
     */
    @ParameterizedTest
    @ValueSource(strings = {" alice", "alice ", "Alice", " ALICE "})
    @WithMockUser(roles = "API")
    void shouldCreateRecordWithCanonicalHandleWhenHandleIsPaddedOrMixedCase(String raw) throws Exception {
        // Arrange: the service only answers for the canonical handle
        when(nip05RecordService.create(eq("alice"), eq("example.com"), eq(TEST_PUBKEY), any()))
                .thenReturn(createTestRecord(1L, "alice", "example.com"));

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", raw, "domain", "example.com", "pubkey", TEST_PUBKEY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("alice")));
    }

    /**
     * imani-wallet#342: normalisation does not repair a handle with an inner space or a
     * forbidden character, and nothing reaches the service.
     */
    @ParameterizedTest
    @ValueSource(strings = {"al ice", "alice!"})
    @WithMockUser(roles = "API")
    void shouldStillRejectHandleThatIsInvalidAfterNormalisation(String raw) throws Exception {
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", raw, "domain", "example.com", "pubkey", TEST_PUBKEY))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        verify(nip05RecordService, never()).create(any(), any(), any(), any());
    }

    /**
     * imani-wallet#342: an existing handle is still a 409 when the claim arrives padded and
     * mixed-case, because the service is asked about the canonical handle.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn409WhenExistingHandleIsClaimedPaddedAndMixedCase() throws Exception {
        // Arrange
        when(nip05RecordService.create(eq("alice"), eq("example.com"), eq(TEST_PUBKEY), any()))
                .thenThrow(new Nip05RecordExistsException("alice@example.com"));

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", " Alice ", "domain", "Example.com", "pubkey", TEST_PUBKEY))))
                .andExpect(status().isConflict());
    }

    /**
     * imani-wallet#342: a lookup by "Alice@Example.com " finds alice@example.com.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldFindRecordByCanonicalNip05WhenLookupIsPaddedOrMixedCase() throws Exception {
        // Arrange
        when(nip05RecordService.findByNip05("alice@example.com"))
                .thenReturn(Optional.of(createTestRecord(1L, "alice", "example.com")));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/by-nip05/{nip05}", "Alice@Example.com "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")));
    }

    /**
     * Tests updating a record successfully.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldUpdateRecordSuccessfully() throws Exception {
        // Arrange
        UpdateNip05RecordRequest request = UpdateNip05RecordRequest.builder()
                .enabled(false)
                .build();

        Nip05RecordData updated = createTestRecord(1L, "alice", "example.com")
                .withEnabled(false);
        when(nip05RecordService.update(eq(1L), any(), any(), eq(false)))
                .thenReturn(updated);

        // Act & Assert
        mockMvc.perform(put(API_BASE + "/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    /**
     * Tests deleting a record successfully.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldDeleteRecordSuccessfully() throws Exception {
        // Arrange
        doNothing().when(nip05RecordService).delete(1L);

        // Act & Assert
        mockMvc.perform(delete(API_BASE + "/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /**
     * Tests that 404 is returned when deleting non-existent record.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn404WhenDeletingNonExistentRecord() throws Exception {
        // Arrange
        doThrow(new Nip05RecordNotFoundException(999L))
                .when(nip05RecordService).delete(999L);

        // Act & Assert
        mockMvc.perform(delete(API_BASE + "/999")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests toggling record enabled status.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldToggleRecordStatus() throws Exception {
        // Arrange
        Nip05RecordData toggled = createTestRecord(1L, "alice", "example.com")
                .withEnabled(false);
        when(nip05RecordService.toggleEnabled(1L)).thenReturn(toggled);

        // Act & Assert
        mockMvc.perform(patch(API_BASE + "/1/toggle")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    /**
     * Tests that unauthenticated requests are rejected.
     */
    @Test
    void shouldRejectUnauthenticatedRequests() throws Exception {
        // Act & Assert
        mockMvc.perform(get(API_BASE))
                .andExpect(status().isUnauthorized());
    }

    private Nip05RecordData createTestRecord(Long id, String username, String domain) {
        return Nip05RecordData.builder()
                .id(id)
                .domainId(1L)
                .username(username)
                .domain(domain)
                .pubkey(TEST_PUBKEY)
                .relaysJson("[]")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
