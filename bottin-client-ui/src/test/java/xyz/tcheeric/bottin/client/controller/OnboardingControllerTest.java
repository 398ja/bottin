package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingController.class)
@org.springframework.context.annotation.Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
@org.springframework.test.context.TestPropertySource(properties = "bottin.client.blossom-url=http://blossom.test:8888")
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderClientRouterAtRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("router"));
    }

    @Test
    void shouldShowStepMethodPage() throws Exception {
        mockMvc.perform(get("/onboarding"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-method"));
    }

    @Test
    void shouldAdvanceToProfileStep() throws Exception {
        mockMvc.perform(post("/onboarding/step-method").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-profile"));
    }

    @Test
    void shouldAdvanceToSecurityStep() throws Exception {
        mockMvc.perform(post("/onboarding/step-profile").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-security"));
    }

    @Test
    void shouldAdvanceToConfirmStep() throws Exception {
        mockMvc.perform(post("/onboarding/step-security").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-confirm"));
    }

    @Test
    void shouldRedirectToWelcomeOnComplete() throws Exception {
        mockMvc.perform(post("/onboarding/complete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding/welcome"));
    }

    @Test
    void shouldShowWelcomePage() throws Exception {
        mockMvc.perform(get("/onboarding/welcome"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-welcome"));
    }

    @Test
    void shouldResolveAvailableLowercaseUsername() throws Exception {
        mockMvc.perform(get("/api/v1/resolve/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void shouldResolveAvailableUsernameWithNumbers() throws Exception {
        mockMvc.perform(get("/api/v1/resolve/user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void shouldResolveUnavailableUsernameForUppercase() throws Exception {
        mockMvc.perform(get("/api/v1/resolve/ALICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void shouldMarkUnavailableForInvalidCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/resolve/alice@example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void shouldMarkUnavailableForOversizedUsername() throws Exception {
        String longUsername = "a".repeat(65);
        mockMvc.perform(get("/api/v1/resolve/" + longUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    /**
     * The onboarding profile step uploads avatars before any account exists, so
     * it needs the Blossom URL on the model just like the profile page.
     */
    @Test
    void shouldExposeBlossomUrlOnTheProfileStep() throws Exception {
        mockMvc.perform(get("/onboarding/step/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }

    /**
     * The profile step is also reached by posting the method step, which renders
     * the same template and therefore needs the same attribute.
     */
    @Test
    void shouldExposeBlossomUrlWhenPostingTheMethodStep() throws Exception {
        mockMvc.perform(post("/onboarding/step-method").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", "http://blossom.test:8888"));
    }

    /**
     * The onboarding profile step now takes images from the device, so it must
     * render file pickers, the hidden fields the uploaded URLs land in, and the
     * Blossom URL the uploader reads.
     */
    @Test
    void shouldRenderImageFilePickersOnTheProfileStep() throws Exception {
        mockMvc.perform(get("/onboarding/step/profile"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"onboarding-picture-file\"")))
                .andExpect(content().string(containsString("id=\"onboarding-banner-file\"")))
                .andExpect(content().string(containsString("name=\"picture\"")))
                .andExpect(content().string(containsString("name=\"banner\"")))
                .andExpect(content().string(containsString("id=\"blossom-url\"")))
                .andExpect(content().string(containsString("http://blossom.test:8888")));
    }
}
