package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.admin.service.AdminDashboardService;
import xyz.tcheeric.bottin.admin.service.AdminDashboardService.DashboardStats;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for AdminDashboardController.
 * Tests dashboard access and statistics display.
 */
@WebMvcTest(AdminDashboardController.class)
@Import(AdminSecurityConfig.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminDashboardService dashboardService;

    /**
     * Tests that unauthenticated users are redirected to login.
     */
    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    /**
     * Tests that authenticated admin can access dashboard.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDisplayDashboardWhenAuthenticated() throws Exception {
        // Arrange
        when(dashboardService.getDashboardStats()).thenReturn(createTestStats());
        when(dashboardService.getRecentRecords(anyInt())).thenReturn(Collections.emptyList());
        when(dashboardService.getRecentDomains(anyInt())).thenReturn(Collections.emptyList());
        when(dashboardService.getPendingVerificationDomains()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("stats"))
                .andExpect(model().attributeExists("recentRecords"))
                .andExpect(model().attributeExists("recentDomains"))
                .andExpect(model().attributeExists("pendingDomains"));
    }

    /**
     * Tests that /admin redirects to /admin/dashboard.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectFromAdminRootToDashboard() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    /**
     * Tests that a caller with no session cannot reach the dashboard.
     *
     * <p>Replaces an earlier test asserting that a principal holding a non-admin
     * role got 403. That scenario is no longer representable: there is no user
     * store to authenticate as somebody else, and the only way to hold a
     * principal is to prove control of the administrator's key. What a role
     * permits is now decided by the permission annotations on each handler,
     * covered by AdminAccessControlTest; what this asserts is the invariant that
     * survives the change — no session, no dashboard.
     */
    @Test
    void shouldDenyAccessWithoutASession() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/dashboard").header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Tests that dashboard stats are correctly added to model.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldPopulateModelWithDashboardStats() throws Exception {
        // Arrange
        DashboardStats stats = DashboardStats.builder()
                .totalRecords(100)
                .enabledRecords(80)
                .disabledRecords(20)
                .totalDomains(10)
                .verifiedDomains(8)
                .pendingVerification(2)
                .build();

        when(dashboardService.getDashboardStats()).thenReturn(stats);
        when(dashboardService.getRecentRecords(anyInt())).thenReturn(Collections.emptyList());
        when(dashboardService.getRecentDomains(anyInt())).thenReturn(Collections.emptyList());
        when(dashboardService.getPendingVerificationDomains()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stats", stats));
    }

    private DashboardStats createTestStats() {
        return DashboardStats.builder()
                .totalRecords(0)
                .enabledRecords(0)
                .disabledRecords(0)
                .totalDomains(0)
                .verifiedDomains(0)
                .pendingVerification(0)
                .build();
    }
}
