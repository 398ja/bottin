package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.service.AdminUserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AdminAdministratorsController.
 *
 * <p>Covers what the page reports for each outcome, which is the half of this
 * feature an operator actually sees. Whether the store was written is asserted
 * in AdminUserServiceTest; whether the caller was allowed to ask at all is
 * asserted where the permission interceptor exists, since a {@code @WebMvcTest}
 * slice does not register it.
 */
@WebMvcTest(AdminAdministratorsController.class)
@Import(AdminSecurityConfig.class)
class AdminAdministratorsControllerTest {

    private static final String HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";
    private static final String NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    /**
     * Tests that an anonymous caller is sent to sign in rather than reaching the
     * management endpoint.
     */
    @Test
    void shouldRedirectAnonymousCallersToSignIn() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators").with(csrf()).param("key", NPUB))
                .andExpect(redirectedUrl("http://localhost/admin/login"));

        verify(adminUserService, never()).add(any(), any(), any());
    }

    /**
     * Tests the ordinary success: the key is passed to the service and the
     * operator is returned to the settings page told it worked.
     */
    @Test
    @WithMockUser
    void shouldAddAnAdministratorAndReportSuccess() throws Exception {
        when(adminUserService.add(eq(NPUB), eq("Ops laptop"), any()))
                .thenReturn(AdminUserService.AdditionOutcome.ADDED);

        mockMvc.perform(post("/admin/settings/administrators").with(csrf())
                        .param("key", NPUB)
                        .param("label", "Ops laptop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(adminUserService).add(eq(NPUB), eq("Ops laptop"), any());
    }

    /**
     * Tests that a key which already administers the deployment is reported
     * informationally rather than as a failure. The state the operator asked for
     * already holds, so there is nothing to fix.
     */
    @Test
    @WithMockUser
    void shouldReportAnAlreadyAdministeringKeyWithoutRaisingAnError() throws Exception {
        when(adminUserService.add(any(), any(), any()))
                .thenReturn(AdminUserService.AdditionOutcome.ALREADY_ADMINISTERS);

        mockMvc.perform(post("/admin/settings/administrators").with(csrf()).param("key", HEX))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("info"))
                .andExpect(flash().attributeCount(1));
    }

    /**
     * Tests that a value which is not a public key is refused with the offending
     * value named, as the settings page already does for relay URLs. Reported as
     * an error, unlike the case above — here the operator does have something to
     * fix.
     */
    @Test
    @WithMockUser
    void shouldReportAValueThatIsNotAKeyAsAnErrorNamingIt() throws Exception {
        when(adminUserService.add(any(), any(), any()))
                .thenThrow(new IllegalArgumentException(
                        "Administrator not added. 'not-a-key' is not a Nostr public key."));

        mockMvc.perform(post("/admin/settings/administrators").with(csrf()).param("key", "not-a-key"))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("error",
                        org.hamcrest.Matchers.containsString("not-a-key")));
    }

    /**
     * Tests that a submission with no key at all does not reach the service.
     */
    @Test
    @WithMockUser
    void shouldNotCallTheServiceWhenNoKeyWasSubmitted() throws Exception {
        mockMvc.perform(post("/admin/settings/administrators").with(csrf()).param("key", ""))
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attributeExists("error"));

        verify(adminUserService, never()).add(any(), any(), any());
    }
}
