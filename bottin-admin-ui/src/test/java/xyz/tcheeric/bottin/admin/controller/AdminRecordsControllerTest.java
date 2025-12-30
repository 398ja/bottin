package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.service.Nip05RecordService;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for AdminRecordsController.
 * Tests NIP-05 record management operations.
 */
@WebMvcTest(AdminRecordsController.class)
@Import(AdminSecurityConfig.class)
class AdminRecordsControllerTest {

    private static final String VALID_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Nip05RecordService recordService;

    /**
     * Tests that unauthenticated users are redirected to login.
     */
    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/records"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    /**
     * Tests that authenticated admin can list records.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDisplayRecordsListWhenAuthenticated() throws Exception {
        // Arrange
        when(recordService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/admin/records"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/records"))
                .andExpect(model().attributeExists("records"))
                .andExpect(model().attributeExists("createForm"));
    }

    /**
     * Tests that search parameter filters records.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldFilterRecordsBySearchParameter() throws Exception {
        // Arrange
        when(recordService.searchByUsername(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/admin/records").param("search", "alice"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/records"))
                .andExpect(model().attribute("search", "alice"));
    }

    /**
     * Tests viewing a single record by ID.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDisplayRecordDetailWhenFound() throws Exception {
        // Arrange
        Nip05RecordData record = createTestRecord(1L, "alice", "example.com");
        when(recordService.findById(1L)).thenReturn(Optional.of(record));

        // Act & Assert
        mockMvc.perform(get("/admin/records/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/record-detail"))
                .andExpect(model().attributeExists("record"))
                .andExpect(model().attributeExists("updateForm"));
    }

    /**
     * Tests redirect when record not found.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectWhenRecordNotFound() throws Exception {
        // Arrange
        when(recordService.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/admin/records/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records?error=notfound"));
    }

    /**
     * Tests creating a new record successfully.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateRecordSuccessfully() throws Exception {
        // Arrange
        Nip05RecordData created = createTestRecord(1L, "alice", "example.com");
        when(recordService.create(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(created);

        // Act & Assert
        mockMvc.perform(post("/admin/records")
                        .with(csrf())
                        .param("username", "alice")
                        .param("domain", "example.com")
                        .param("pubkey", VALID_PUBKEY))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests that validation errors redirect back with error message.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectWithErrorOnValidationFailure() throws Exception {
        // Act & Assert: missing required fields
        mockMvc.perform(post("/admin/records")
                        .with(csrf())
                        .param("username", "")
                        .param("domain", "")
                        .param("pubkey", "invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records"))
                .andExpect(flash().attributeExists("error"));
    }

    /**
     * Tests toggling record enabled status.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldToggleRecordSuccessfully() throws Exception {
        // Arrange
        Nip05RecordData record = createTestRecord(1L, "alice", "example.com");
        when(recordService.toggleEnabled(1L)).thenReturn(record);

        // Act & Assert
        mockMvc.perform(post("/admin/records/1/toggle")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests deleting a record.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteRecordSuccessfully() throws Exception {
        // Arrange
        doNothing().when(recordService).delete(1L);

        // Act & Assert
        mockMvc.perform(post("/admin/records/1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests updating a record.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldUpdateRecordSuccessfully() throws Exception {
        // Arrange
        when(recordService.update(anyLong(), anyString(), anyList(), isNull())).thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/admin/records/1/update")
                        .with(csrf())
                        .param("pubkey", VALID_PUBKEY)
                        .param("relaysText", "wss://relay.example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records/1"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests that CSRF token is required for POST requests.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRequireCsrfTokenForPostRequests() throws Exception {
        // Act & Assert: POST without CSRF token
        mockMvc.perform(post("/admin/records/1/delete"))
                .andExpect(status().isForbidden());
    }

    private Nip05RecordData createTestRecord(Long id, String username, String domain) {
        return Nip05RecordData.builder()
                .id(id)
                .domainId(1L)
                .username(username)
                .domain(domain)
                .pubkey(VALID_PUBKEY)
                .relaysJson("[]")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
