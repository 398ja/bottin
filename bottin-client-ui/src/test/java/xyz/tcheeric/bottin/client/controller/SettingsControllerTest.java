package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldShowSettingsIndex() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "settings/index"));
    }

    @Test
    void shouldShowSecurityPage() throws Exception {
        mockMvc.perform(get("/settings/security"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "settings/security"));
    }

    /**
     * The profile card on the settings page opens the editable form directly, rather
     * than the read-only view that would make the user click through a second time.
     */
    @Test
    void shouldLinkTheProfileCardToTheEditForm() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(content().string(containsString("href=\"/profile/edit\"")));
    }

    /**
     * The relays settings route renders through the shared layout so the client-side
     * relay editor has a page to attach to.
     */
    @Test
    void shouldShowRelaysPage() throws Exception {
        mockMvc.perform(get("/settings/relays"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "settings/relays"));
    }
}
