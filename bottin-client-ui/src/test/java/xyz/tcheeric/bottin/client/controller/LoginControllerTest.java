package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldShowLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "login"));
    }

    @Test
    void shouldSetCorrectTitle() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("title", "Sign In"));
    }

    /**
     * Tests that this page only unlocks an identity the browser already holds.
     * Accepting a key here signed the user in without storing an identity or
     * fetching their profile, leaving the app blank; entering a key belongs to
     * the entry flow, which encrypts and stores it.
     */
    @Test
    void shouldNotOfferNsecEntry() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("passphrase-input")))
                .andExpect(content().string(not(containsString("nsec-input"))))
                .andExpect(content().string(not(containsString("handleLogin"))));
    }
}
