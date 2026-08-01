package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.service.SettingsService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
 * Unit tests for AdminSettingsController.
 *
 * <p>Tests the settings page an operator uses to configure the deployment, and
 * in particular that invalid input is refused at the form rather than reaching
 * the service.
 */
@WebMvcTest(AdminSettingsController.class)
@Import(AdminSecurityConfig.class)
class AdminSettingsControllerTest {

    private static final String MEDIA_SERVER = "https://blossom.example";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettingsService settingsService;

    /**
     * Tests that the settings page is not reachable without signing in, since
     * it exposes and changes the deployment's configuration.
     */
    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    /**
     * Tests that the page renders a form bound to the stored settings, so an
     * operator edits the current values rather than a blank slate.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRenderFormBoundToStoredSettingsWhenAuthenticated() throws Exception {
        // Arrange
        when(settingsService.find()).thenReturn(storedSettings());

        // Act & Assert
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeExists("settingsForm"))
                .andExpect(model().attributeExists("updatedAt"));
    }

    /**
     * Tests that a valid submission is stored and reported back, so an operator
     * knows the change landed.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldSaveSettingsAndRedirectWithSuccessFlash() throws Exception {
        // Arrange
        when(settingsService.update(any(SettingsData.class))).thenReturn(storedSettings());

        // Act & Assert
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("blossomUrl", MEDIA_SERVER)
                        .param("defaultRelays", "ws://relay-a:7777\nwss://relay-b.example")
                        .param("discoveryRelays", "wss://relay.damus.io")
                        .param("rateLimitPerMinute", "30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(settingsService).update(any(SettingsData.class));
    }

    /**
     * Tests that an unconfigured media server cannot be saved, since it would
     * silently disable image uploads for every user.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectBlankMediaServerWithoutReachingTheService() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("blossomUrl", "")
                        .param("defaultRelays", "")
                        .param("discoveryRelays", "")
                        .param("rateLimitPerMinute", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "blossomUrl"));

        verify(settingsService, never()).update(any());
    }

    /**
     * Tests that a relay URL no Nostr client could connect to is refused at the
     * form, before the service is asked to store it.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectNonWebsocketRelayWithoutReachingTheService() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("blossomUrl", MEDIA_SERVER)
                        .param("defaultRelays", "http://relay-b.example")
                        .param("discoveryRelays", "")
                        .param("rateLimitPerMinute", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "defaultRelays"));

        verify(settingsService, never()).update(any());
    }

    /**
     * Tests that a rate limit of zero is refused, since a limit of zero would
     * reject every request rather than allowing an unlimited one.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectRateLimitBelowOneWithoutReachingTheService() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("blossomUrl", MEDIA_SERVER)
                        .param("defaultRelays", "")
                        .param("discoveryRelays", "")
                        .param("rateLimitPerMinute", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "rateLimitPerMinute"));

        verify(settingsService, never()).update(any());
    }

    /**
     * Tests that a rejection raised by the service, which validates again for
     * callers that do not come through this form, is reported to the operator
     * rather than surfacing as a server error.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReportServiceRejectionAsAnError() throws Exception {
        // Arrange
        when(settingsService.update(any(SettingsData.class)))
                .thenThrow(new IllegalArgumentException("Relay URL must start with ws:// or wss://: bad"));

        // Act & Assert
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("blossomUrl", MEDIA_SERVER)
                        .param("defaultRelays", "wss://relay-a.example")
                        .param("discoveryRelays", "")
                        .param("rateLimitPerMinute", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeExists("error"));
    }

    private SettingsData storedSettings() {
        return SettingsData.builder()
                .blossomUrl(MEDIA_SERVER)
                .defaultRelays(List.of("ws://relay-a:7777"))
                .discoveryRelays(List.of("wss://relay.damus.io"))
                .rateLimitPerMinute(30)
                .updatedAt(Instant.parse("2026-08-01T12:00:00Z"))
                .build();
    }
}
