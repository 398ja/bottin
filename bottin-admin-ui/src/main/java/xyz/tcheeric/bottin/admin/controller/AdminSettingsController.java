package xyz.tcheeric.bottin.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.spring.annotation.RequiresPermission;
import xyz.tcheeric.bottin.admin.dto.SettingsForm;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.service.SettingsService;

/**
 * Controller for the admin-maintained deployment settings.
 *
 * <p>Writes through {@link SettingsService} directly, as
 * {@link AdminDomainsController} does with the domain service. There is no REST
 * equivalent: a write endpoint would be a second write path with a second
 * authentication story and no caller.
 */
@RequiresPermission(AdminPermissions.READ)
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
@Slf4j
public class AdminSettingsController {

    private static final String SETTINGS_VIEW = "admin/settings";

    private final SettingsService settingsService;

    @GetMapping
    public String viewSettings(Model model) {
        SettingsData settings = settingsService.find();
        model.addAttribute("settingsForm", SettingsForm.from(settings));
        model.addAttribute("updatedAt", settings.getUpdatedAt());
        return SETTINGS_VIEW;
    }

    /**
     * Saves the submitted settings.
     *
     * <p>A rejected submission re-renders the form rather than redirecting, so
     * the operator keeps what they typed alongside the error that explains it.
     */
    @RequiresPermission(AdminPermissions.WRITE)
    @PostMapping
    public String saveSettings(
            @Valid @ModelAttribute("settingsForm") SettingsForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            log.warn("admin_settings_update_rejected reason=validation error_count={}",
                    bindingResult.getErrorCount());
            return SETTINGS_VIEW;
        }

        try {
            SettingsData saved = settingsService.update(form.toSettingsData());
            log.info("admin_settings_updated media_server_configured={} system_relays={} discovery_relays={} rate_limit_per_minute={}",
                    saved.getBlossomUrl() != null, saved.getDefaultRelays().size(),
                    saved.getDiscoveryRelays().size(), saved.getRateLimitPerMinute());
            redirectAttributes.addFlashAttribute("success", "Settings saved");
            return "redirect:/admin/settings";
        } catch (IllegalArgumentException e) {
            log.warn("admin_settings_update_rejected reason=invalid_value error={}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return SETTINGS_VIEW;
        }
    }
}
