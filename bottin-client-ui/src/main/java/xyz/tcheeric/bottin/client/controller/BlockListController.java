package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings")
public class BlockListController {

    @GetMapping("/blocks")
    public String blocks(Model model) {
        model.addAttribute("title", "Blocked Users");
        model.addAttribute("content", "settings/blocks");
        return "layout";
    }
}
