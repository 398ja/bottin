package xyz.tcheeric.bottin.client.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.tcheeric.bottin.client.service.DirectorySettingsClient;

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

    @GetMapping("/{pubkey}")
    public String userProfile(@PathVariable String pubkey, Model model) {
        if (pubkey == null || !pubkey.matches("[0-9a-f]{64}")) {
            return "redirect:/profile";
        }
        model.addAttribute("title", "Profile");
        model.addAttribute("content", "profile");
        model.addAttribute("profilePubkey", pubkey);
        return "layout";
    }
}
