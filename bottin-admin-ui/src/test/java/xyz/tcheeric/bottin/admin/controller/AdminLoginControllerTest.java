package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.admin.security.AdminKeyState;
import xyz.tcheeric.bottin.admin.security.ConfiguredAdminAclResolver;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for AdminLoginController.
 *
 * <p>The sign-in page has to tell an administrator what they need, and that
 * depends on state it can read before anyone signs in. Offering a form when the
 * deployment cannot admit anybody would leave an operator unable to tell a wrong
 * key from a missing one, so each configuration state is covered.
 */
@WebMvcTest(AdminLoginController.class)
@Import(AdminSecurityConfig.class)
class AdminLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfiguredAdminAclResolver adminAclResolver;

    /**
     * Tests that the page is reachable by somebody who is not signed in, since
     * that is the only kind of visitor it exists for.
     */
    @Test
    void shouldServeTheSignInPageWithoutAuthentication() throws Exception {
        // Arrange
        when(adminAclResolver.keyState()).thenReturn(AdminKeyState.CONFIGURED);

        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"));
    }

    /**
     * Tests that a configured deployment offers the key and passphrase fields,
     * and no username or password.
     */
    @Test
    void shouldOfferTheKeyFormWhenTheDeploymentIsConfigured() throws Exception {
        // Arrange
        when(adminAclResolver.keyState()).thenReturn(AdminKeyState.CONFIGURED);

        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("deploymentReady", true))
                .andExpect(content().string(containsString("id=\"admin-nsec\"")))
                .andExpect(content().string(containsString("id=\"admin-passphrase\"")))
                .andExpect(content().string(not(containsString("name=\"username\""))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    /**
     * Tests that an unconfigured deployment says so instead of presenting a form
     * that cannot succeed.
     */
    @Test
    void shouldExplainWhenNoAdministratorKeyIsConfigured() throws Exception {
        // Arrange
        when(adminAclResolver.keyState()).thenReturn(AdminKeyState.NOT_CONFIGURED);

        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("deploymentReady", false))
                .andExpect(content().string(containsString("No administrator key is configured")))
                .andExpect(content().string(containsString("BOTTIN_ADMIN_NPUB")))
                .andExpect(content().string(not(containsString("id=\"admin-nsec\""))));
    }

    /**
     * Tests that a configured-but-unusable value reads differently from a
     * missing one. They are different operator mistakes with different fixes,
     * and FR-006 requires them to be distinguishable.
     */
    @Test
    void shouldDistinguishAnUnreadableKeyFromAMissingOne() throws Exception {
        // Arrange
        when(adminAclResolver.keyState()).thenReturn(AdminKeyState.UNREADABLE);

        // Act & Assert
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("deploymentReady", false))
                .andExpect(content().string(containsString("not readable")))
                .andExpect(content().string(not(containsString("No administrator key is configured"))))
                .andExpect(content().string(not(containsString("id=\"admin-nsec\""))));
    }
}
