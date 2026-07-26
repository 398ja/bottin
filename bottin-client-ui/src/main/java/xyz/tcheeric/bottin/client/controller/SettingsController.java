package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    @GetMapping
    public String index(Model model) {
        model.addAttribute("title", "Settings");
        model.addAttribute("content", "settings/index");
        return "layout";
    }

    @GetMapping("/security")
    public String security(Model model) {
        model.addAttribute("title", "Security");
        model.addAttribute("content", "settings/security");
        return "layout";
    }

    @GetMapping("/relays")
    public String relays(Model model) {
        model.addAttribute("title", "Relays");
        model.addAttribute("content", "settings/relays");
        return "layout";
    }
}
