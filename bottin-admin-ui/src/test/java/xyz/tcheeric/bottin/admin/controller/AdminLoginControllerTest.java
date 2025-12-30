package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for AdminLoginController.
 * Tests the admin login page accessibility and parameter handling.
 */
@WebMvcTest(AdminLoginController.class)
@Import(AdminSecurityConfig.class)
class AdminLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests that the login page is accessible without authentication.
     */
    @Test
    void shouldDisplayLoginPageWithoutAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"));
    }

    /**
     * Tests that error message is displayed when login fails.
     */
    @Test
    void shouldDisplayErrorMessageWhenLoginFails() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attributeExists("error"));
    }

    /**
     * Tests that success message is displayed after logout.
     */
    @Test
    void shouldDisplaySuccessMessageAfterLogout() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attributeExists("message"));
    }

    /**
     * Tests that neither error nor message is displayed on initial load.
     */
    @Test
    void shouldNotDisplayMessagesOnInitialLoad() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(model().attributeDoesNotExist("error"))
                .andExpect(model().attributeDoesNotExist("message"));
    }
}
