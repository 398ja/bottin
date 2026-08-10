package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationException;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingController.class)
@org.springframework.context.annotation.Import(xyz.tcheeric.bottin.client.config.ClientProperties.class)
class OnboardingControllerTest {

    private static final String MEDIA_SERVER = "http://blossom.test:8888";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DirectoryRegistrationService registrationService;

    @MockitoBean
    private DirectorySettingsClient settingsClient;

    @org.junit.jupiter.api.BeforeEach
    void configureMediaServer() {
        when(settingsClient.current())
                .thenReturn(new DirectorySettings(MEDIA_SERVER, List.of(), List.of()));
    }

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

    /**
     * Tests that registering is a single step, reached from the entry page. The
     * three screens it replaced — profile, security and review — no longer exist,
     * so this is the only thing between choosing to register and having an account.
     */
    @Test
    void shouldShowTheRegisterStep() throws Exception {
        // Given: a visitor who chose to register
        // When: the register step is requested
        mockMvc.perform(get("/onboarding/step/register"))
                // Then: the single registration screen renders
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "onboarding/step-register"));
    }

    /**
     * Tests that the multi-step wizard's intermediate posts are gone rather than
     * merely unreachable from the UI. A route that still answers is a route that
     * can still be addressed directly, and each of these rendered a template this
     * feature deleted.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/onboarding/step-method", "/onboarding/step-profile", "/onboarding/step-security"})
    void shouldNoLongerServeTheRemovedWizardSteps(String removedRoute) throws Exception {
        // Given: a route from the four-step wizard
        // When: it is posted to directly
        mockMvc.perform(post(removedRoute).with(csrf()))
                // Then: nothing answers
                .andExpect(status().isNotFound());
    }

    /**
     * Tests that the register step asks for a handle and two passwords and
     * nothing else. This is the feature's whole claim, so it is asserted on the
     * rendered form rather than on the template name.
     */
    @Test
    void shouldOfferOnlyAHandleAndTwoPasswordsOnTheRegisterStep() throws Exception {
        // Given: the register step
        // When: it is rendered
        mockMvc.perform(get("/onboarding/step/register"))
                // Then: the three inputs are present
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"confirm\"")))
                // And: none of the profile fields it used to collect are
                .andExpect(content().string(not(containsString("name=\"display_name\""))))
                .andExpect(content().string(not(containsString("name=\"about\""))))
                .andExpect(content().string(not(containsString("name=\"picture\""))))
                .andExpect(content().string(not(containsString("name=\"banner\""))))
                .andExpect(content().string(not(containsString("name=\"lud16\""))))
                .andExpect(content().string(not(containsString("name=\"website\""))));
    }

    /**
     * Tests that the register step says the password cannot be recovered. The
     * word "password" ordinarily implies a reset link, and here there is none:
     * the deployment holds nothing that could restore access.
     */
    @Test
    void shouldWarnThatThePasswordCannotBeRecovered() throws Exception {
        // Given: the register step
        // When: it is rendered
        mockMvc.perform(get("/onboarding/step/register"))
                // Then: the consequence of forgetting it is stated
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be reset or recovered")));
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
     * Tests that registering asks for no image at all. Uploading one used to
     * require a key before the account existed, which is why a key was minted
     * early and stashed; with the field gone, neither the upload nor that
     * workaround has any reason to run.
     */
    @Test
    void shouldNotOfferImageUploadsDuringRegistration() throws Exception {
        // Given: the register step
        // When: it is rendered
        mockMvc.perform(get("/onboarding/step/register"))
                // Then: there is nothing to upload with, and no media server to upload to
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("type=\"file\""))))
                .andExpect(content().string(not(containsString("id=\"blossom-url\""))))
                .andExpect(content().string(not(containsString(MEDIA_SERVER))));
    }

    /**
     * Tests that the import step is handed the relays to search for an existing
     * profile: the administrator's discovery relays followed by the deployment's
     * system relays, because a key that registered here published its profile to
     * the latter while one created elsewhere published to the former.
     */
    @Test
    void shouldExposeDiscoveryRelaysOnTheImportStep() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(new DirectorySettings(
                MEDIA_SERVER,
                List.of("ws://relay-a:7777"),
                List.of("wss://relay.damus.io", "wss://nos.lol")));

        // Act & Assert
        mockMvc.perform(get("/onboarding/step/import"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("discoveryRelays",
                        "wss://relay.damus.io,wss://nos.lol,ws://relay-a:7777"));
    }

    /**
     * Tests that a relay serving as both a discovery and a system relay is
     * queried once rather than twice.
     */
    @Test
    void shouldNotRepeatARelayListedAsBothDiscoveryAndSystem() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(new DirectorySettings(
                MEDIA_SERVER,
                List.of("wss://shared.example"),
                List.of("wss://shared.example")));

        // Act & Assert
        mockMvc.perform(get("/onboarding/step/import"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("discoveryRelays", "wss://shared.example"));
    }

    /**
     * Tests that an unconfigured deployment still renders the import step. The
     * profile lookup then finds nothing and sign-in proceeds, since signing in
     * must never hinge on a relay being configured.
     */
    @Test
    void shouldRenderTheImportStepWhenNoRelaysAreConfigured() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(DirectorySettings.unconfigured());

        // Act & Assert
        mockMvc.perform(get("/onboarding/step/import"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("discoveryRelays", ""));
    }
}
