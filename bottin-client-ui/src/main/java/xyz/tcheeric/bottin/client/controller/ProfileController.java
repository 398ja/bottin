package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @GetMapping
    public String ownProfile(Model model) {
        model.addAttribute("title", "My Profile");
        model.addAttribute("content", "profile");
        return "layout";
    }

    @GetMapping("/{pubkey}")
    public String userProfile(@PathVariable String pubkey, Model model) {
        model.addAttribute("title", "Profile");
        model.addAttribute("content", "profile");
        model.addAttribute("profilePubkey", pubkey);
        return "layout";
    }
}
