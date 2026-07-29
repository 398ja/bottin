package xyz.tcheeric.bottin.client.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import xyz.tcheeric.bottin.client.config.ClientProperties;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationException;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class OnboardingController {

    private static final Map<String, String> STEP_TITLES = Map.of(
            "method", "Get Started",
            "profile", "Profile Setup",
            "security", "Set Password",
            "confirm", "Review",
            "import", "Import Identity"
    );

    private static final String USERNAME_PATTERN = "[a-z0-9_-]{1,64}";

    private final ClientProperties clientProperties;
    private final DirectoryRegistrationService registrationService;

    @GetMapping("/")
    public String root() {
        return "router";
    }

    @GetMapping("/onboarding")
    public String stepMethod(Model model) {
        model.addAttribute("title", "Get Started");
        model.addAttribute("content", "onboarding/step-method");
        return "layout";
    }

    @PostMapping("/onboarding/step-method")
    public String postStepMethod(Model model) {
        model.addAttribute("title", "Profile Setup");
        model.addAttribute("content", "onboarding/step-profile");
        model.addAttribute("bottinDomain", clientProperties.getDomain());
        model.addAttribute("blossomUrl", clientProperties.getBlossomUrl());
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
            model.addAttribute("bottinDomain", clientProperties.getDomain());
            model.addAttribute("blossomUrl", clientProperties.getBlossomUrl());
        }
        return "layout";
    }

    @GetMapping(value = "/api/v1/resolve/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> resolveUsername(@PathVariable String username) {
        return asJson(resolve(username));
    }

    @GetMapping("/api/v1/resolve")
    @ResponseBody
    public ResponseEntity<?> resolveByQuery(@RequestParam String username, HttpServletRequest request) {
        Availability availability = resolve(username);
        if (request.getHeader("HX-Request") != null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(availability.badge());
        }
        return ResponseEntity.ok(asJson(availability));
    }

    /**
     * A handle is only offered when it passes the character rules and the directory
     * holds no record for it, so onboarding cannot end on a name that registration
     * will reject. An unreachable directory is reported as such rather than as a
     * free handle.
     */
    private Availability resolve(String username) {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            return Availability.INVALID;
        }
        try {
            return registrationService.isTaken(username) ? Availability.TAKEN : Availability.AVAILABLE;
        } catch (DirectoryRegistrationException e) {
            // The service logs the failure; the badge asks the user to try again.
            return Availability.UNKNOWN;
        }
    }

    private Map<String, Object> asJson(Availability availability) {
        return Map.of("available", availability.available, "status", availability.name().toLowerCase());
    }

    /**
     * Outcome of a handle check together with the badge the onboarding form shows.
     */
    private enum Availability {
        AVAILABLE(true, "#22c55e", "\u2713 Available"),
        TAKEN(false, "#ef4444", "\u2717 Already taken"),
        INVALID(false, "#ef4444", "\u2717 Not available (lowercase letters, numbers, hyphens, underscores only)"),
        UNKNOWN(false, "#f59e0b", "Could not check availability \u2014 try again");

        private final boolean available;
        private final String colour;
        private final String message;

        Availability(boolean available, String colour, String message) {
            this.available = available;
            this.colour = colour;
            this.message = message;
        }

        /**
         * The badge carries the outcome as {@code data-available} so the form can
         * gate its Continue button on it without reading the message text.
         */
        private String badge() {
            return "<span data-available=\"" + available + "\""
                    + " style=\"color: " + colour + "; font-size: 0.875rem;\">" + message + "</span>";
        }
    }
}
