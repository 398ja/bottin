package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.dto.DirectorySettings;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    private static final String MEDIA_SERVER = "http://blossom.test:8888";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DirectorySettingsClient settingsClient;

    @BeforeEach
    void configureMediaServer() {
        when(settingsClient.current())
                .thenReturn(new DirectorySettings(MEDIA_SERVER, List.of(), List.of()));
    }

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
                .andExpect(content().string(not(containsString("id=\"profile-pubkey\""))))
                .andExpect(content().string(not(containsString("id=\"profile-save-btn\""))));
    }

    /**
     * Another key's profile page carries that key for the view script to read, and
     * drops the edit link: the page shows their published identity, and nobody but
     * its owner can change it.
     */
    @Test
    void shouldRenderAnotherKeysProfileWithoutTheEditLink() throws Exception {
        String pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

        mockMvc.perform(get("/profile/" + pubkey))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"profile-pubkey\"")))
                .andExpect(content().string(containsString(pubkey)))
                .andExpect(content().string(not(containsString("href=\"/profile/edit\""))))
                // The script only replaces this once it has read the relays, so
                // markup saying "Your profile" is painted on a stranger's page.
                .andExpect(content().string(not(containsString("Your profile"))));
    }

    /**
     * An npub in the URL reaches the same profile as its hex, because an npub is
     * the form people copy and paste. The page carries the canonical hex, which
     * is what the relay query filters on.
     */
    @Test
    void shouldAcceptAnNpubAndRenderItsCanonicalHex() throws Exception {
        String npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
        String hex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

        mockMvc.perform(get("/profile/" + npub))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profilePubkey", hex))
                .andExpect(content().string(containsString(hex)));
    }

    /**
     * A value that is not a public key is not found. It deliberately does not
     * redirect to the reader's own profile, which answered a stranger's broken
     * link with "here is you" and so read as though the link had worked.
     */
    @Test
    void shouldNotFindAProfileForSomethingThatIsNotAPublicKey() throws Exception {
        mockMvc.perform(get("/profile/not-a-key"))
                .andExpect(status().isNotFound());
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
     * The edit page uploads images straight to the Blossom server, so the
     * configured URL has to reach that template. The read-only profile view
     * never uploads, so it carries no such attribute.
     */
    @Test
    void shouldExposeBlossomUrlToTheProfileEditPage() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", MEDIA_SERVER));
    }

    /**
     * Avatar and banner are chosen from the device now, so the page must render
     * file inputs and the Blossom URL the uploader reads, not URL text boxes.
     */
    @Test
    void shouldRenderImageFilePickersAndBlossomUrl() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("blossomUrl", MEDIA_SERVER))
                .andExpect(content().string(containsString("id=\"profile-picture\" type=\"file\"")))
                .andExpect(content().string(containsString("id=\"profile-banner\" type=\"file\"")))
                .andExpect(content().string(containsString("id=\"profile-preview-avatar\"")))
                .andExpect(content().string(containsString("id=\"profile-preview-banner\"")))
                .andExpect(content().string(containsString("id=\"profile-picture-remove\"")))
                .andExpect(content().string(containsString("id=\"profile-banner-remove\"")))
                .andExpect(content().string(containsString("id=\"blossom-url\"")))
                .andExpect(content().string(containsString(MEDIA_SERVER)));
    }

    /**
     * Tests that an unconfigured deployment still renders the page and the span,
     * carrying no URL. The uploader reads that empty value and disables its
     * controls with a stated reason rather than posting nowhere.
     */
    @Test
    void shouldRenderTheProfileEditorWithoutAMediaServerWhenUnconfigured() throws Exception {
        // Arrange
        when(settingsClient.current()).thenReturn(DirectorySettings.unconfigured());

        // Act & Assert
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"blossom-url\"")))
                .andExpect(content().string(not(containsString("blossom.test"))));
    }
}
