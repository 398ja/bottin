package xyz.tcheeric.bottin.client.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;
import xyz.tcheeric.bottin.core.nostr.NostrPublicKeys;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final DirectorySettingsClient settingsClient;

    @GetMapping
    public String ownProfile(Model model) {
        model.addAttribute("title", "My Profile");
        model.addAttribute("content", "profile");
        return "layout";
    }

    @GetMapping("/edit")
    public String editOwnProfile(Model model) {
        model.addAttribute("title", "Edit Profile");
        model.addAttribute("content", "profile-edit");
        model.addAttribute("blossomUrl", settingsClient.current().blossomUrl());
        return "layout";
    }

    /**
     * Another key's profile, addressed as hex or as an npub because both are
     * written down and pasted. A value that is not a public key is not found
     * rather than redirected to the reader's own profile: answering a stranger's
     * broken link with "here is you" reads as though the link worked.
     *
     * <p>Carries no relays: the page is public, and a reader who is not signed in
     * resolves the deployment's relays from {@code /api/v1/relays/system}, which
     * is the one relay route with no session guard.
     */
    @GetMapping("/{key}")
    public String userProfile(@PathVariable String key, Model model) {
        String pubkey = NostrPublicKeys.toCanonicalHex(key)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "'" + key + "' is not a Nostr public key"));

        model.addAttribute("title", "Profile");
        model.addAttribute("content", "profile");
        model.addAttribute("profilePubkey", pubkey);
        return "layout";
    }
}
