package xyz.tcheeric.bottin.client.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class OnboardingController {

    private static final Map<String, String> STEP_TITLES = Map.of(
            "method", "Create Account",
            "profile", "Profile Setup",
            "security", "Set Password",
            "confirm", "Review"
    );

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

    @GetMapping("/onboarding/step/{step}")
    public String step(@PathVariable String step, Model model) {
        String title = STEP_TITLES.get(step);
        if (title == null) {
            return "redirect:/onboarding";
        }
        model.addAttribute("title", title);
        model.addAttribute("content", "onboarding/step-" + step);
        if ("profile".equals(step)) {
            model.addAttribute("bottinDomain", bottinDomain);
        }
        return "layout";
    }

    @GetMapping(value = "/api/v1/resolve/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> resolveUsername(@PathVariable String username) {
        return resolve(username);
    }

    @GetMapping("/api/v1/resolve")
    @ResponseBody
    public ResponseEntity<?> resolveByQuery(@RequestParam String username, HttpServletRequest request) {
        boolean available = username != null && username.matches("[a-z0-9_-]{1,64}");
        if (request.getHeader("HX-Request") != null) {
            String message = available
                    ? "<span style=\"color: var(--color-success, #22c55e); font-size: 0.875rem;\">\u2713 Available</span>"
                    : "<span style=\"color: var(--color-error, #ef4444); font-size: 0.875rem;\">\u2717 Not available (lowercase letters, numbers, hyphens, underscores only)</span>";
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(message);
        }
        return ResponseEntity.ok(Map.of("available", available));
    }

    private Map<String, Object> resolve(String username) {
        boolean available = username != null && username.matches("[a-z0-9_-]{1,64}");
        return Map.of("available", available);
    }
}
