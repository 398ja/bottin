package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings")
public class FollowListController {

    @GetMapping("/follows")
    public String follows(Model model) {
        model.addAttribute("title", "Followed Users");
        model.addAttribute("content", "settings/follows");
        return "layout";
    }
}
