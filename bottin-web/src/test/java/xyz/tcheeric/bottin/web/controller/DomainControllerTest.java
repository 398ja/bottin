package xyz.tcheeric.bottin.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.verification.DomainVerificationService;
import xyz.tcheeric.bottin.web.config.SecurityConfig;
import xyz.tcheeric.bottin.web.dto.CreateDomainRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for DomainController.
 * Tests REST API endpoints for domain management.
 */
@WebMvcTest(DomainController.class)
@Import(SecurityConfig.class)
class DomainControllerTest {

    private static final String API_BASE = "/api/v1/domains";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DomainService domainService;

    @MockBean
    private DomainVerificationService verificationService;

    /**
     * Tests that listing domains returns a paginated response.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnPaginatedDomainsWhenListingAll() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "example.com", true);
        when(domainService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(domain)));

        // Act & Assert
        mockMvc.perform(get(API_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("example.com")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    /**
     * Tests that getting all domains (unpaginated) works.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnAllDomainsUnpaginated() throws Exception {
        // Arrange
        DomainData domain1 = createTestDomain(1L, "example.com", true);
        DomainData domain2 = createTestDomain(2L, "test.com", false);
        when(domainService.findAll()).thenReturn(List.of(domain1, domain2));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /**
     * Tests that getting a domain by ID returns the domain.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnDomainWhenFoundById() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "example.com", true);
        when(domainService.findById(1L)).thenReturn(Optional.of(domain));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("example.com")))
                .andExpect(jsonPath("$.verified", is(true)));
    }

    /**
     * Tests that 404 is returned when domain not found by ID.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn404WhenDomainNotFoundById() throws Exception {
        // Arrange
        when(domainService.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests getting a domain by name.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnDomainWhenFoundByName() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "example.com", true);
        when(domainService.findByName("example.com")).thenReturn(Optional.of(domain));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/by-name/example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("example.com")));
    }

    /**
     * Tests creating a new domain successfully.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldCreateDomainSuccessfully() throws Exception {
        // Arrange
        CreateDomainRequest request = CreateDomainRequest.builder()
                .name("newdomain.com")
                .build();

        DomainData created = createTestDomain(1L, "newdomain.com", false);
        when(domainService.create(eq("newdomain.com"), any()))
                .thenReturn(created);

        // Act & Assert
        mockMvc.perform(post(API_BASE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("newdomain.com")))
                .andExpect(jsonPath("$.verified", is(false)));
    }

    /**
     * Tests that validation errors are returned for invalid create request.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnValidationErrorForInvalidCreateRequest() throws Exception {
        // Arrange: Create request with invalid domain name
        CreateDomainRequest request = CreateDomainRequest.builder()
                .name("-invalid-")
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
     * Tests deleting a domain successfully.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldDeleteDomainSuccessfully() throws Exception {
        // Arrange
        doNothing().when(domainService).delete(1L);

        // Act & Assert
        mockMvc.perform(delete(API_BASE + "/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /**
     * Tests that 404 is returned when deleting non-existent domain.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturn404WhenDeletingNonExistentDomain() throws Exception {
        // Arrange
        doThrow(new DomainNotFoundException("999"))
                .when(domainService).delete(999L);

        // Act & Assert
        mockMvc.perform(delete(API_BASE + "/999")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /**
     * Tests listing verified domains.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnVerifiedDomains() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "verified.com", true);
        when(domainService.findVerified()).thenReturn(List.of(domain));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].verified", is(true)));
    }

    /**
     * Tests listing unverified domains.
     */
    @Test
    @WithMockUser(roles = "API")
    void shouldReturnUnverifiedDomains() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "pending.com", false);
        when(domainService.findUnverified()).thenReturn(List.of(domain));

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/unverified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].verified", is(false)));
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

    private DomainData createTestDomain(Long id, String name, boolean verified) {
        return DomainData.builder()
                .id(id)
                .name(name)
                .verified(verified)
                .verifiedAt(verified ? Instant.now() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .recordCount(0)
                .build();
    }
}
