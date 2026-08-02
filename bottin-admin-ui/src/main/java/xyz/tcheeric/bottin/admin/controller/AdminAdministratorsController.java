package xyz.tcheeric.bottin.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.bottin.admin.dto.AddAdministratorForm;
import xyz.tcheeric.bottin.service.AdminUserService;
import xyz.tcheeric.nap.spring.annotation.RequiresPermission;

/**
 * Adds and removes the administrators who may sign in to the dashboard.
 *
 * <p>Separate from {@link AdminSettingsController} although it posts to the same
 * page: changing who may administer a deployment is a different responsibility
 * from changing what the deployment does, held by a different role, and gated on
 * a different permission.
 *
 * <p>{@code MANAGE_ADMINS} is what enforces that difference. An added
 * administrator's session never carries it, so these handlers refuse them even
 * when the request is made directly rather than through a page — the settings
 * template merely declines to offer controls that would be refused anyway.
 */
@RequiresPermission(AdminPermissions.MANAGE_ADMINS)
@Controller
@RequestMapping("/admin/settings/administrators")
@RequiredArgsConstructor
@Slf4j
public class AdminAdministratorsController {

    private static final String SETTINGS_REDIRECT = "redirect:/admin/settings";

    private final AdminUserService adminUserService;

    /**
     * Adds an administrator.
     *
     * <p>Every outcome returns to the settings page, distinguished by which
     * message it carries: {@code success} when a key was added, {@code info}
     * when the key already administers the deployment and nothing changed, and
     * {@code error} only when the operator has something to fix.
     *
     * <p>Redirecting rather than re-rendering, unlike the settings form: this
     * controller does not own the settings page's model, and reconstructing it
     * here to preserve one typed field would put the settings page together in
     * two places. The error names the offending value, so nothing needed to
     * correct it is lost.
     */
    @PostMapping
    public String addAdministrator(@Valid @ModelAttribute("addAdministratorForm") AddAdministratorForm form,
                                   BindingResult bindingResult,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {

        String actingAdministrator = authentication == null ? null : authentication.getName();

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
            log.warn("administrator_add_rejected reason=validation attempted_by={}", actingAdministrator);
            redirectAttributes.addFlashAttribute("error", message);
            return SETTINGS_REDIRECT;
        }

        try {
            AdminUserService.AdditionOutcome outcome =
                    adminUserService.add(form.getKey(), form.getLabel(), actingAdministrator);
            report(outcome, form, redirectAttributes);
        } catch (IllegalArgumentException e) {
            log.warn("administrator_add_rejected reason=invalid_key attempted_by={}", actingAdministrator);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return SETTINGS_REDIRECT;
    }

    /**
     * Says which of the two non-error things happened. "Already administers" is
     * deliberately not an error: an operator who pasted the wrong key still
     * needs to know nothing was granted, but has broken nothing.
     */
    private void report(AdminUserService.AdditionOutcome outcome,
                        AddAdministratorForm form,
                        RedirectAttributes redirectAttributes) {

        if (outcome == AdminUserService.AdditionOutcome.ADDED) {
            String named = form.getLabel() == null || form.getLabel().isBlank()
                    ? form.getKey()
                    : form.getLabel();
            redirectAttributes.addFlashAttribute("success", "Added " + named + " as an administrator.");
            return;
        }

        redirectAttributes.addFlashAttribute("info",
                "That key already administers this deployment. Nothing was changed.");
    }
}
