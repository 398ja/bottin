package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationException;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
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

    @MockitoBean
    private DirectoryRegistrationService registrationService;

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
     * Tests that a handle the directory already holds is reported as unavailable,
     * so onboarding cannot end on a name registration would reject.
     */
    @Test
    void shouldMarkUnavailableWhenTheDirectoryHoldsTheHandle() throws Exception {
        // Given: the directory already has a record for this handle
        when(registrationService.isTaken("alice")).thenReturn(true);

        // When: availability is checked
        mockMvc.perform(get("/api/v1/resolve/alice"))
                // Then: the handle is not offered
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("taken"));
    }

    /**
     * Tests that an unreachable directory is reported as an unknown result rather
     * than as a free handle, which would only fail later at registration.
     */
    @Test
    void shouldNotOfferHandleWhenTheDirectoryCannotBeReached() throws Exception {
        // Given: the directory cannot be reached
        when(registrationService.isTaken("alice"))
                .thenThrow(new DirectoryRegistrationException(
                        DirectoryRegistrationException.DIRECTORY_UNAVAILABLE, "unreachable", null));

        // When: availability is checked
        mockMvc.perform(get("/api/v1/resolve/alice"))
                // Then: the handle is withheld and the state says why
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("unknown"));
    }

    /**
     * Tests that the inline badge the onboarding form swaps in names the taken
     * handle and carries the state the form gates its Continue button on.
     */
    @Test
    void shouldRenderTakenBadgeForHtmxRequests() throws Exception {
        // Given: a handle that is already registered
        when(registrationService.isTaken("alice")).thenReturn(true);

        // When: the form asks over HTMX
        mockMvc.perform(get("/api/v1/resolve").param("username", "alice").header("HX-Request", "true"))
                // Then: the badge says the handle is taken and marks it unavailable
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Already taken")))
                .andExpect(content().string(containsString("data-available=\"false\"")));
    }

    /**
     * Tests that a free handle's badge marks it available, which is what re-enables
     * the Continue button after a taken handle was corrected.
     */
    @Test
    void shouldRenderAvailableBadgeForHtmxRequests() throws Exception {
        // Given: a handle no one holds
        when(registrationService.isTaken("alice")).thenReturn(false);

        // When: the form asks over HTMX
        mockMvc.perform(get("/api/v1/resolve").param("username", "alice").header("HX-Request", "true"))
                // Then: the badge marks the handle available
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-available=\"true\"")));
    }

    /**
     * Tests that the entry page offers Register and Login, naming what the visitor
     * is doing rather than the key material involved.
     */
    @Test
    void shouldOfferRegisterAndLoginOnTheEntryPage() throws Exception {
        // Given: the entry step
        // When: it is rendered
        mockMvc.perform(get("/onboarding"))
                // Then: the options are named for the visitor's intent
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>Register</strong>")))
                .andExpect(content().string(containsString("<strong>Login</strong>")))
                .andExpect(content().string(not(containsString("Create New Key"))))
                .andExpect(content().string(not(containsString("Import Existing Key"))));
    }

    /**
     * Tests that each method choice is a label wrapping its radio, so clicking
     * anywhere on the card selects it through the browser. Selecting it from an
     * onclick handler sets the property without firing a change event, which left
     * the nsec field hidden until the page was reloaded.
     */
    @Test
    void shouldMakeEachMethodCardALabelForItsRadio() throws Exception {
        // Given: the entry step where the visitor picks create or import
        // When: it is rendered
        mockMvc.perform(get("/onboarding"))
                // Then: the cards are labels and nothing selects a radio from script
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<label class=\"card-radio\">")))
                .andExpect(content().string(containsString("<label class=\"card-radio mt-2\">")))
                .andExpect(content().string(not(containsString("checked=true"))));
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
