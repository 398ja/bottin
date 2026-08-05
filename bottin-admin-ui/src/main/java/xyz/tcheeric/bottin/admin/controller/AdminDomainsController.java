package xyz.tcheeric.bottin.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.spring.annotation.RequiresPermission;
import xyz.tcheeric.bottin.admin.dto.CreateDomainForm;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.verification.DomainVerificationService;
import xyz.tcheeric.bottin.verification.VerificationChallenge;
import xyz.tcheeric.bottin.verification.VerificationResult;
import xyz.tcheeric.bottin.verification.VerificationStatus;

import jakarta.validation.Valid;

/**
 * Controller for domain management.
 *
 * <p>Reading the domain list is {@link AdminPermissions#READ}, so every
 * administrator can see which domains the deployment answers for — a records
 * page that lets you pick a domain is unusable to someone forbidden to know
 * they exist.
 *
 * <p>Changing that list is {@link AdminPermissions#MANAGE_DOMAINS}, held by the
 * super administrator alone. All four mutating routes moved together —
 * creation, both verification steps, and deletion — rather than creation only.
 * Splitting them would leave an administrator able to delete a domain they
 * could not recreate, and able to drive verification of a name they could not
 * have added; a capability that is destructive in one direction only is a worse
 * boundary than either extreme.
 */
@RequiresPermission(AdminPermissions.READ)
@Controller
@RequestMapping("/admin/domains")
@RequiredArgsConstructor
@Slf4j
public class AdminDomainsController {

    private final DomainService domainService;
    private final DomainVerificationService verificationService;

    @GetMapping
    public String listDomains(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication,
            Model model) {

        Page<DomainData> domains = domainService.findAll(pageable);
        model.addAttribute("domains", domains);
        model.addAttribute("createForm", new CreateDomainForm());
        model.addAttribute("verificationMethods", VerificationMethod.values());
        model.addAttribute("canManageDomains", holdsManageDomains(authentication));
        return "admin/domains";
    }

    /**
     * Whether to offer the controls that change the domain list.
     *
     * <p>The guard that matters is {@code @RequiresPermission} on each route;
     * this only decides what the page shows. Offering an administrator a button
     * that always answers 403 tells them the deployment is broken rather than
     * that the capability is not theirs, so the page withholds the control and
     * says why — as the settings page does for administrator management.
     */
    private boolean holdsManageDomains(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> AdminPermissions.MANAGE_DOMAINS.equals(granted.getAuthority()));
    }

    @GetMapping("/{id}")
    public String viewDomain(@PathVariable Long id, Authentication authentication, Model model) {
        boolean canManageDomains = holdsManageDomains(authentication);

        return domainService.findById(id)
                .map(domain -> {
                    // Auto-initiate verification for unverified domains without a token,
                    // but only for someone allowed to drive verification. This is a GET
                    // with a side effect, so without the guard it would hand an
                    // administrator holding READ alone the very action that
                    // POST /verify refuses them — the boundary would exist only for
                    // callers who used the button.
                    if (canManageDomains && !domain.isVerified() && domain.getVerificationToken() == null) {
                        verificationService.initiateVerification(id);
                        // Reload domain to get the new token
                        domain = domainService.findById(id).orElse(domain);
                    }

                    VerificationStatus status = verificationService.getVerificationStatus(id);
                    model.addAttribute("domain", domain);
                    model.addAttribute("verificationStatus", status);
                    model.addAttribute("verificationMethods", VerificationMethod.values());
                    model.addAttribute("canManageDomains", canManageDomains);
                    return "admin/domain-detail";
                })
                .orElse("redirect:/admin/domains?error=notfound");
    }

    @RequiresPermission(AdminPermissions.MANAGE_DOMAINS)
    @PostMapping
    public String createDomain(
            @Valid @ModelAttribute("createForm") CreateDomainForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Validation failed: " + bindingResult.getAllErrors());
            return "redirect:/admin/domains";
        }

        try {
            DomainData created = domainService.create(form.getName(), form.getOwnerPubkey());
            log.info("admin_domain_created id={} name={}", created.getId(), created.getName());
            redirectAttributes.addFlashAttribute("success", "Domain created successfully");
        } catch (Exception e) {
            log.error("admin_domain_create_failed error={}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create domain: " + e.getMessage());
        }

        return "redirect:/admin/domains";
    }

    @RequiresPermission(AdminPermissions.MANAGE_DOMAINS)
    @PostMapping("/{id}/verify")
    public String initiateVerification(
            @PathVariable Long id,
            @RequestParam VerificationMethod method,
            RedirectAttributes redirectAttributes) {

        try {
            VerificationChallenge challenge = verificationService.initiateVerification(id, method);

            if (challenge.isAlreadyVerified()) {
                redirectAttributes.addFlashAttribute("info", "Domain is already verified");
            } else {
                redirectAttributes.addFlashAttribute("success", "Verification initiated");
                redirectAttributes.addFlashAttribute("challenge", challenge);
            }

            log.info("admin_verification_initiated domain_id={} method={}", id, method);
        } catch (Exception e) {
            log.error("admin_verification_initiate_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to initiate verification: " + e.getMessage());
        }

        return "redirect:/admin/domains/" + id;
    }

    @RequiresPermission(AdminPermissions.MANAGE_DOMAINS)
    @PostMapping("/{id}/verify/attempt")
    public String attemptVerification(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            VerificationResult result = verificationService.attemptVerification(id);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", "Domain verified successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Verification failed: " + result.getMessage());
            }

            log.info("admin_verification_attempt domain_id={} success={}", id, result.isSuccess());
        } catch (Exception e) {
            log.error("admin_verification_attempt_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Verification attempt failed: " + e.getMessage());
        }

        return "redirect:/admin/domains/" + id;
    }

    @RequiresPermission(AdminPermissions.MANAGE_DOMAINS)
    @PostMapping("/{id}/delete")
    public String deleteDomain(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            domainService.delete(id);
            log.info("admin_domain_deleted id={}", id);
            redirectAttributes.addFlashAttribute("success", "Domain deleted successfully");
        } catch (Exception e) {
            log.error("admin_domain_delete_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete domain: " + e.getMessage());
        }

        return "redirect:/admin/domains";
    }
}
