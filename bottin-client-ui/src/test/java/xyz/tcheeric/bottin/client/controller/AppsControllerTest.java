package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Regression coverage for the /apps landing page and the authenticated nav /
 * avatar dropdown it carries. The plan that introduced this feature was marked
 * Done while the dropdown never rendered and no test caught it; these tests lock
 * the fixes (client-revealed nav, svg avatar default, real-navigation logout) in
 * place at the level where each defect was actually observable — the rendered
 * HTML of the shared nav fragment.
 */
@WebMvcTest(AppsController.class)
class AppsControllerTest {

    private static final String DEFAULT_AVATAR_ASSET = "static/img/default-avatar.svg";
    private static final String BROKEN_AVATAR_ASSET = "static/img/default-avatar.png";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests that /apps renders through the shared layout with the apps content
     * fragment, so a logged-in user lands on a real page rather than a 404.
     */
    @Test
    void shouldRenderAppsPageThroughLayout() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "apps"));
    }

    /**
     * Tests that the authenticated nav section is present in the rendered HTML so
     * the client can reveal it once a session is confirmed. The original defect
     * gated this block on a server-side principal that page routes never carry,
     * so the section never rendered at all — this assertion would have caught it.
     */
    @Test
    void shouldRenderAuthenticatedNavSectionSoClientCanReveal() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(content().string(containsString("id=\"nav-authed\"")))
                .andExpect(content().string(containsString("id=\"nav-avatar\"")))
                .andExpect(content().string(containsString("href=\"/search\"")));
    }

    /**
     * Tests that the nav avatar references the bundled svg default and never the
     * non-existent .png that produced a broken-image icon.
     */
    @Test
    void shouldReferenceExistingSvgAvatarNotBrokenPng() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(content().string(containsString("default-avatar.svg")))
                .andExpect(content().string(not(containsString("default-avatar.png"))));
    }

    /**
     * Tests that the avatar dropdown exposes Profile, Settings and Logout, the
     * three menu items the feature promised.
     */
    @Test
    void shouldExposeProfileSettingsAndLogoutInDropdown() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(content().string(containsString(">Profile<")))
                .andExpect(content().string(containsString(">Settings<")))
                .andExpect(content().string(containsString(">Logout<")));
    }

    /**
     * Tests that Logout triggers a real navigation via APP.logout() rather than
     * the old hx-post body swap, which blanked the page by replacing the body
     * with the logout endpoint's empty 200 response.
     */
    @Test
    void shouldWireLogoutToRealNavigationNotBodySwap() throws Exception {
        mockMvc.perform(get("/apps"))
                .andExpect(content().string(containsString("APP.logout()")))
                .andExpect(content().string(not(containsString("hx-post=\"/api/v1/auth/logout\""))))
                .andExpect(content().string(not(containsString("hx-target=\"body\""))));
    }

    /**
     * Tests that the svg avatar the nav references actually exists on the
     * classpath and the broken .png does not, so the rendered src resolves to a
     * real asset.
     */
    @Test
    void shouldBundleSvgAvatarAssetAndNotThePng() {
        assertThat(new ClassPathResource(DEFAULT_AVATAR_ASSET).exists()).isTrue();
        assertThat(new ClassPathResource(BROKEN_AVATAR_ASSET).exists()).isFalse();
    }
}
