package xyz.tcheeric.bottin.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to redirect root path to admin dashboard.
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToAdmin() {
        return "redirect:/admin";
    }
}
