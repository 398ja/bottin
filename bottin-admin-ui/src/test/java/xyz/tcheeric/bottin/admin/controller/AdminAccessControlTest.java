package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.admin.security.ConfiguredAdminAclResolver;
import xyz.tcheeric.bottin.admin.service.AdminDashboardService;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.service.Nip05RecordService;
import xyz.tcheeric.bottin.service.SettingsService;
import xyz.tcheeric.bottin.verification.DomainVerificationService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every route under the admin dashboard refuses a caller with no
 * session.
 *
 * <p>Replacing an authentication mechanism wholesale is exactly when a route
 * quietly loses its guard, and the loss is silent: the page simply works for
 * everybody. The routes are therefore enumerated rather than sampled, so adding
 * one without protection fails this suite instead of passing unnoticed.
 *
 * <p>This covers refusal only. That a signed-in administrator is actually served
 * each page is covered by the individual controller tests, which already stub
 * the services each page needs; asserting it again here would mean duplicating
 * all of that setup to re-test what they cover. The request under test never
 * reaches a controller, which is why no service stubbing is needed.
 */
@WebMvcTest({AdminDashboardController.class, AdminRecordsController.class,
        AdminDomainsController.class, AdminSettingsController.class})
@Import(AdminSecurityConfig.class)
class AdminAccessControlTest {

    private static final String SIGN_IN_URL = "http://localhost/admin/login";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDashboardService dashboardService;

    @MockBean
    private Nip05RecordService recordService;

    @MockBean
    private DomainService domainService;

    @MockBean
    private DomainVerificationService verificationService;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private ConfiguredAdminAclResolver adminAclResolver;

    /**
     * Tests that a browser with no session is sent to sign in rather than served
     * the page, for every page the dashboard has.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/admin",
            "/admin/dashboard",
            "/admin/records",
            "/admin/records/1",
            "/admin/domains",
            "/admin/domains/1",
            "/admin/settings"
    })
    void shouldRedirectEveryAdminPageToSignInWithoutASession(String path) throws Exception {
        mockMvc.perform(get(path).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(SIGN_IN_URL));
    }

    /**
     * Tests that a caller which does not want HTML gets a status code rather
     * than a sign-in page, so an API added here later reports failure honestly.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/admin/dashboard", "/admin/records", "/admin/domains", "/admin/settings"})
    void shouldAnswerUnauthorizedForNonBrowserCallers(String path) throws Exception {
        mockMvc.perform(get(path).header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Tests that every state-changing action is refused without a session. A
     * valid CSRF token is supplied so that what is being tested is the missing
     * session rather than the missing token.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/records",
            "/admin/records/1/update",
            "/admin/records/1/toggle",
            "/admin/records/1/delete",
            "/admin/domains",
            "/admin/domains/1/verify",
            "/admin/domains/1/verify/attempt",
            "/admin/domains/1/delete",
            "/admin/settings"
    })
    void shouldRefuseEveryAdminActionWithoutASession(String path) throws Exception {
        // Asserts the redirect specifically, not merely a non-2xx status: an
        // unguarded route reaching an unstubbed service would fail with 500,
        // which any "not successful" assertion would accept as proof of a guard
        // that is no longer there.
        mockMvc.perform(post(path).with(csrf()).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(SIGN_IN_URL));
    }
}
