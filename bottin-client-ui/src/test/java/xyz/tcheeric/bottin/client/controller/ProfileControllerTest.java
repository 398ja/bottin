package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(ProfileController.class)
@org.springframework.context.annotation.Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@org.springframework.test.context.TestPropertySource(properties = "bottin.client.blossom-url=http://blossom.test:8888")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldShowOwnProfile() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "profile"));
    }

    @Test
    void shouldSetOwnProfileTitle() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("title", "My Profile"));
    }

    @Test
    void shouldShowUserProfileByPubkey() throws Exception {
        String pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
        mockMvc.perform(get("/profile/" + pubkey))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "profile"))
                .andExpect(model().attribute("profilePubkey", pubkey));
    }

    @Test
    void shouldSetUserProfileTitle() throws Exception {
        String pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
        mockMvc.perform(get("/profile/" + pubkey))
                .andExpect(status().isOk())
                .andExpect(model().attribute("title", "Profile"));
    }

    /**
     * The edit route renders its own template rather than the read-only profile,
     * and is matched literally instead of falling into the /{pubkey} mapping.
     */
    @Test
    void shouldShowEditProfilePage() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "profile-edit"))
                .andExpect(model().attribute("title", "Edit Profile"));
    }

    /**
     * The profile page presents identity read-only: banner, avatar and NIP-05
     * placeholders the view script fills, with no editable form or save button.
     */
    @Test
    void shouldRenderReadOnlyProfileView() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"profile-banner-image\"")))
                .andExpect(content().string(containsString("id=\"profile-avatar\"")))
                .andExpect(content().string(containsString("id=\"profile-nip05\"")))
                .andExpect(content().string(containsString("href=\"/profile/edit\"")))
                .andExpect(content().string(not(containsString("id=\"profile-save-btn\""))));
    }

    /**
     * The edit page renders the editable form fields the client script binds to,
     * so a missing or renamed field ID is caught at build time.
     */
    @Test
    void shouldRenderProfileEditFormFields() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"profile-display-name\"")))
                .andExpect(content().string(containsString("id=\"profile-about\"")))
                .andExpect(content().string(containsString("id=\"profile-picture\"")))
                .andExpect(content().string(containsString("id=\"profile-banner\"")))
                .andExpect(content().string(containsString("id=\"profile-lud16\"")))
                .andExpect(content().string(containsString("id=\"profile-website\"")))
                .andExpect(content().string(containsString("id=\"profile-save-btn\"")));
    }

    /**
     * The profile page uploads images straight to the Blossom server, so the
     * configured URL has to reach the template.
     */
    @Test
    void shouldExposeBlossomUrlToTheProfilePage() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }
}
