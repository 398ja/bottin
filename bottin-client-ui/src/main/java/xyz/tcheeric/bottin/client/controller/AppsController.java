package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppsController {

    @GetMapping("/apps")
    public String appsPage(Model model) {
        model.addAttribute("title", "Apps");
        model.addAttribute("content", "apps");
        return "layout";
    }
}
