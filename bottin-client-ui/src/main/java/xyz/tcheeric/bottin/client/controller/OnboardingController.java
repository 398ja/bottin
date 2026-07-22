package xyz.tcheeric.bottin.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class OnboardingController {

    @Value("${bottin.client.domain:bottin.example.com}")
    private String bottinDomain;

    @GetMapping("/")
    public String root() {
        return "redirect:/onboarding";
    }

    @GetMapping("/onboarding")
    public String stepMethod(Model model) {
        model.addAttribute("title", "Create Account");
        model.addAttribute("content", "onboarding/step-method");
        return "layout";
    }

    @PostMapping("/onboarding/step-method")
    public String postStepMethod(Model model) {
        model.addAttribute("title", "Profile Setup");
        model.addAttribute("content", "onboarding/step-profile");
        model.addAttribute("bottinDomain", bottinDomain);
        return "layout";
    }

    @PostMapping("/onboarding/step-profile")
    public String postStepProfile(Model model) {
        model.addAttribute("title", "Set Password");
        model.addAttribute("content", "onboarding/step-security");
        return "layout";
    }

    @PostMapping("/onboarding/step-security")
    public String postStepSecurity(Model model) {
        model.addAttribute("title", "Review");
        model.addAttribute("content", "onboarding/step-confirm");
        return "layout";
    }

    @PostMapping("/onboarding/complete")
    public String complete() {
        return "redirect:/onboarding/welcome";
    }

    @GetMapping("/onboarding/welcome")
    public String welcome(Model model) {
        model.addAttribute("title", "Welcome");
        model.addAttribute("content", "onboarding/step-welcome");
        return "layout";
    }

    @GetMapping("/api/v1/resolve/{username}")
    @ResponseBody
    public Map<String, Object> resolveUsername(@PathVariable String username) {
        boolean available = username != null && username.matches("[a-z0-9_-]{1,64}");
        return Map.of("available", available);
    }
}
