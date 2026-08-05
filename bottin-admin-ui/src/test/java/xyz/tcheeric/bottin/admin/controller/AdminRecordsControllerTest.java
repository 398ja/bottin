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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    /** Supplies the domain picker; empty unless a test says otherwise. */
    @MockBean
    private xyz.tcheeric.bottin.service.DomainService domainService;

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
     * Tests that no domain means no records, and specifically that the whole
     * table is not fetched.
     *
     * <p>Asserted on {@code findAll} never being called, not merely on an empty
     * model: the page was unusable precisely because it listed every record
     * across every domain, so "returns nothing" and "asks for nothing" are
     * different claims and it is the second one that matters.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldShowNoRecordsUntilADomainIsChosen() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/records"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/records"))
                .andExpect(model().attribute("selectedDomain", (Object) null))
                .andExpect(model().attribute("records",
                        org.hamcrest.Matchers.hasProperty("empty", org.hamcrest.Matchers.is(true))));

        verify(recordService, never()).findAll(any(Pageable.class));
    }

    /**
     * Tests that a search with no domain chosen still lists nothing, rather than
     * falling back to searching every domain at once.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldNotSearchAcrossEveryDomainWhenNoneIsChosen() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/records").param("search", "alice"))
                .andExpect(status().isOk());

        verify(recordService, never()).searchByUsername(anyString(), any(Pageable.class));
        verify(recordService, never()).findAll(any(Pageable.class));
    }

    /**
     * Tests that choosing a domain lists that domain's records.
     *
     * <p>This is also what the <em>View Records</em> link on a domain now sends:
     * {@code ?domain=}, where it previously sent {@code ?search=} and so matched
     * the domain name against usernames and returned nothing.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListOnlyTheChosenDomainsRecords() throws Exception {
        // Arrange
        when(recordService.findByDomain(eq("example.com"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createTestRecord(1L, "alice", "example.com"))));

        // Act & Assert
        mockMvc.perform(get("/admin/records").param("domain", "example.com"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedDomain", "example.com"));

        verify(recordService).findByDomain(eq("example.com"), any(Pageable.class));
        verify(recordService, never()).findAll(any(Pageable.class));
    }

    /**
     * Tests that the username search is scoped to the chosen domain rather than
     * run across all of them.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldScopeTheUsernameSearchToTheChosenDomain() throws Exception {
        // Arrange
        when(recordService.searchByUsernameInDomain(eq("example.com"), eq("ali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/admin/records")
                        .param("domain", "example.com")
                        .param("search", "ali"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedDomain", "example.com"))
                .andExpect(model().attribute("search", "ali"));

        verify(recordService).searchByUsernameInDomain(eq("example.com"), eq("ali"), any(Pageable.class));
        verify(recordService, never()).searchByUsername(anyString(), any(Pageable.class));
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
     *
     * <p>The redirect keeps the domain. The list shows nothing until one is
     * chosen, so returning to the bare path would answer "record created" with an
     * empty page and make the operator pick the domain again to see it.
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
                .andExpect(redirectedUrl("/admin/records?domain=example.com"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests that deleting a record returns to the list of the domain it was in.
     *
     * <p>The domain is read before the delete, since afterwards there is no
     * record left to ask.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnToTheDomainsListAfterDeletingARecord() throws Exception {
        // Arrange
        when(recordService.findById(1L))
                .thenReturn(Optional.of(createTestRecord(1L, "alice", "example.com")));
        doNothing().when(recordService).delete(anyLong());

        // Act & Assert
        mockMvc.perform(post("/admin/records/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/records?domain=example.com"));
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
