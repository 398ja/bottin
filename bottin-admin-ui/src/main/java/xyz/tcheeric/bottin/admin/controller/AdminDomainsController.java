package xyz.tcheeric.bottin.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
 */
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
            Model model) {

        Page<DomainData> domains = domainService.findAll(pageable);
        model.addAttribute("domains", domains);
        model.addAttribute("createForm", new CreateDomainForm());
        model.addAttribute("verificationMethods", VerificationMethod.values());
        return "admin/domains";
    }

    @GetMapping("/{id}")
    public String viewDomain(@PathVariable Long id, Model model) {
        return domainService.findById(id)
                .map(domain -> {
                    VerificationStatus status = verificationService.getVerificationStatus(id);
                    model.addAttribute("domain", domain);
                    model.addAttribute("verificationStatus", status);
                    model.addAttribute("verificationMethods", VerificationMethod.values());
                    return "admin/domain-detail";
                })
                .orElse("redirect:/admin/domains?error=notfound");
    }

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
