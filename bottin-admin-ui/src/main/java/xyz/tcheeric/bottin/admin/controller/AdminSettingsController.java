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
import org.springframework.security.core.Authentication;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.admin.dto.AddAdministratorForm;
import xyz.tcheeric.nap.spring.annotation.RequiresPermission;
import xyz.tcheeric.bottin.admin.dto.SettingsForm;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.service.AdminUserService;
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

    private final AdminUserService adminUserService;

    @GetMapping
    public String viewSettings(Model model, Authentication authentication) {
        SettingsData settings = settingsService.find();
        model.addAttribute("settingsForm", SettingsForm.from(settings));
        model.addAttribute("updatedAt", settings.getUpdatedAt());
        addAdministratorsSection(model, authentication);
        return SETTINGS_VIEW;
    }

    /**
     * Puts the administrator list on the page.
     *
     * <p>Shown to every administrator, so anyone with access can see who else
     * has it. Only a viewer holding {@code MANAGE_ADMINS} is offered the
     * controls — which is presentation, not enforcement: the same permission is
     * checked again by {@link AdminAdministratorsController}, so a request made
     * without going through this page is refused just the same.
     *
     * <p>Collections are always present, never null, so the template never has
     * to guard against absence.
     */
    private void addAdministratorsSection(Model model, Authentication authentication) {
        model.addAttribute("administrators", adminUserService.list());
        model.addAttribute("superAdminPubkey", adminUserService.superAdministratorKey().orElse(null));
        model.addAttribute("canManageAdministrators", holdsManageAdmins(authentication));
        model.addAttribute("addAdministratorForm", new AddAdministratorForm());
    }

    private boolean holdsManageAdmins(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> AdminPermissions.MANAGE_ADMINS.equals(granted.getAuthority()));
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
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            log.warn("admin_settings_update_rejected reason=validation error_count={}",
                    bindingResult.getErrorCount());
            // Re-rendering means rebuilding the whole page, administrators
            // included — the section is part of this view whatever went wrong
            // with the settings form.
            addAdministratorsSection(model, authentication);
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
            addAdministratorsSection(model, authentication);
            return SETTINGS_VIEW;
        }
    }
}
