package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
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
}
