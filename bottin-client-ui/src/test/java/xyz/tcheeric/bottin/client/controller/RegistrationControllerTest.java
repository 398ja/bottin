package xyz.tcheeric.bottin.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationException;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;
import xyz.tcheeric.nap.core.SessionRecord;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the handle registration endpoint the browser calls at the end of onboarding:
 * the pubkey comes from the NAP session rather than the request, and directory
 * failures are surfaced with a code the browser can explain to the user.
 */
@WebMvcTest(RegistrationController.class)
class RegistrationControllerTest {

    private static final String SESSION_PUBKEY = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DirectoryRegistrationService registrationService;

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Tests that a registration is stored against the pubkey of the NAP session,
     * not one supplied by the caller, and the resulting NIP-05 is returned.
     */
    @Test
    void shouldRegisterHandleForSessionPubkey() throws Exception {
        // Given: an authenticated onboarding session and a directory that accepts the handle
        givenNapSession();
        when(registrationService.register(eq("alice"), eq(SESSION_PUBKEY), any()))
                .thenReturn("alice@imani.test");

        // When: the browser posts the chosen handle and its write relays
        mockMvc.perform(post("/api/v1/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "relays", List.of("wss://relay.one")))))
                // Then: the record is confirmed with its NIP-05 identifier
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("registered"))
                .andExpect(jsonPath("$.nip05").value("alice@imani.test"));

        verify(registrationService).register("alice", SESSION_PUBKEY, List.of("wss://relay.one"));
    }

    /**
     * Tests that a request without a NAP session is rejected, so a handle cannot
     * be claimed anonymously.
     */
    @Test
    void shouldRejectRequestWithoutNapSession() throws Exception {
        // Given: no NAP session on the request
        // When: a handle registration is attempted
        mockMvc.perform(post("/api/v1/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice"))))
                // Then: the caller is told to sign in first and nothing is registered
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAP_SESSION_REQUIRED"));

        verify(registrationService, never()).register(any(), any(), any());
    }

    /**
     * Tests that a username outside the allowed character set is rejected locally
     * rather than forwarded to the directory.
     */
    @Test
    void shouldRejectInvalidUsernameWithoutCallingDirectory() throws Exception {
        // Given: an authenticated session and a username the directory does not allow
        givenNapSession();
        // When: it is submitted
        mockMvc.perform(post("/api/v1/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "Alice Smith!"))))
                // Then: it fails validation and the directory is left alone
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));

        verify(registrationService, never()).register(any(), any(), any());
    }

    /**
     * Tests that a handle already held by someone else is reported as a conflict
     * so the browser can tell the user to choose another.
     */
    @Test
    void shouldReportTakenHandleAsConflict() throws Exception {
        // Given: an authenticated session and a directory that already holds the handle
        givenNapSession();
        when(registrationService.register(any(), any(), any()))
                .thenThrow(new DirectoryRegistrationException(
                        DirectoryRegistrationException.USERNAME_TAKEN, "already registered", null));

        // When: the handle is submitted
        mockMvc.perform(post("/api/v1/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "alice"))))
                // Then: the conflict is passed through with its code
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    /**
     * Puts a NAP session in the security context, the way
     * {@link NapSessionFilter} does once it has validated the session cookie.
     */
    private void givenNapSession() {
        SessionRecord session = SessionRecord.create(
                "session-id", "challenge-id", "access-token",
                "npub1test", SESSION_PUBKEY,
                List.of(), List.of(),
                0L, Long.MAX_VALUE);
        SecurityContextHolder.getContext()
                .setAuthentication(new NapSessionFilter.NapAuthenticationToken(session));
    }
}
