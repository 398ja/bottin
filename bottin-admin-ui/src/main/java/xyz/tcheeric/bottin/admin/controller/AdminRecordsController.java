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
import org.springframework.web.util.UriUtils;
import xyz.tcheeric.bottin.admin.config.AdminPermissions;
import xyz.tcheeric.nap.spring.annotation.RequiresPermission;
import xyz.tcheeric.bottin.admin.dto.CreateRecordForm;
import xyz.tcheeric.bottin.admin.dto.UpdateRecordForm;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.service.Nip05RecordService;

import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;

/**
 * Controller for NIP-05 records management.
 */
@RequiresPermission(AdminPermissions.READ)
@Controller
@RequestMapping("/admin/records")
@RequiredArgsConstructor
@Slf4j
public class AdminRecordsController {

    private final Nip05RecordService recordService;

    /**
     * Supplies the domain picker. Every domain, not only the verified ones: an
     * unverified domain can already hold records, and leaving it out of the list
     * would make them unreachable from this page.
     */
    private final DomainService domainService;

    @GetMapping
    public String listRecords(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {

        boolean domainChosen = domain != null && !domain.isBlank();
        boolean searching = search != null && !search.isBlank();

        // No domain, no records. A deployment serving several domains has a
        // record list too long to be read as one, and rendering the whole of it
        // by default is what made the page unusable. An empty table would read
        // as "this domain has no records", so the page says why instead.
        Page<Nip05RecordData> records;
        if (!domainChosen) {
            records = Page.empty(pageable);
        } else if (searching) {
            records = recordService.searchByUsernameInDomain(domain, search, pageable);
        } else {
            records = recordService.findByDomain(domain, pageable);
        }

        model.addAttribute("records", records);
        model.addAttribute("domains", domainService.findAll());
        model.addAttribute("selectedDomain", domainChosen ? domain : null);
        model.addAttribute("search", searching ? search : null);
        model.addAttribute("createForm", new CreateRecordForm());
        return "admin/records";
    }

    @GetMapping("/{id}")
    public String viewRecord(@PathVariable Long id, Model model) {
        return recordService.findById(id)
                .map(record -> {
                    model.addAttribute("record", record);
                    model.addAttribute("updateForm", UpdateRecordForm.from(record));
                    return "admin/record-detail";
                })
                .orElse("redirect:/admin/records?error=notfound");
    }

    @RequiresPermission(AdminPermissions.WRITE)
    @PostMapping
    public String createRecord(
            @Valid @ModelAttribute("createForm") CreateRecordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Validation failed: " + bindingResult.getAllErrors());
            return redirectToRecordsFor(form.getDomain());
        }

        try {
            Nip05RecordData created = recordService.create(
                    form.getUsername(),
                    form.getDomain(),
                    form.getPubkey(),
                    form.getRelays()
            );

            log.info("admin_record_created id={} nip05={}@{}", created.getId(), form.getUsername(), form.getDomain());
            redirectAttributes.addFlashAttribute("success", "Record created successfully");
        } catch (Exception e) {
            log.error("admin_record_create_failed error={}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create record: " + e.getMessage());
        }

        return redirectToRecordsFor(form.getDomain());
    }

    /**
     * Returns to the records list with a domain still chosen.
     *
     * <p>The list shows nothing until one is, so returning to the bare path after
     * an edit would answer "record created" with an empty page and make the
     * operator pick the domain again to see what they just did.
     */
    private String redirectToRecordsFor(String domain) {
        if (domain == null || domain.isBlank()) {
            return "redirect:/admin/records";
        }
        return "redirect:/admin/records?domain=" + UriUtils.encodeQueryParam(domain, StandardCharsets.UTF_8);
    }

    /** The domain a record belongs to, for returning to the list it was edited from. */
    private String domainOf(Long recordId) {
        return recordService.findById(recordId).map(Nip05RecordData::getDomain).orElse(null);
    }

    @RequiresPermission(AdminPermissions.WRITE)
    @PostMapping("/{id}/update")
    public String updateRecord(
            @PathVariable Long id,
            @Valid @ModelAttribute("updateForm") UpdateRecordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Validation failed");
            return "redirect:/admin/records/" + id;
        }

        try {
            recordService.update(id, form.getPubkey(), form.getRelays(), null);
            log.info("admin_record_updated id={}", id);
            redirectAttributes.addFlashAttribute("success", "Record updated successfully");
        } catch (Exception e) {
            log.error("admin_record_update_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update record: " + e.getMessage());
        }

        return "redirect:/admin/records/" + id;
    }

    @RequiresPermission(AdminPermissions.WRITE)
    @PostMapping("/{id}/toggle")
    public String toggleRecord(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String domain = domainOf(id);
        try {
            recordService.toggleEnabled(id);
            log.info("admin_record_toggled id={}", id);
            redirectAttributes.addFlashAttribute("success", "Record toggled successfully");
        } catch (Exception e) {
            log.error("admin_record_toggle_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to toggle record: " + e.getMessage());
        }

        return redirectToRecordsFor(domain);
    }

    @RequiresPermission(AdminPermissions.WRITE)
    @PostMapping("/{id}/delete")
    public String deleteRecord(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Read before deleting: afterwards there is no record to ask.
        String domain = domainOf(id);
        try {
            recordService.delete(id);
            log.info("admin_record_deleted id={}", id);
            redirectAttributes.addFlashAttribute("success", "Record deleted successfully");
        } catch (Exception e) {
            log.error("admin_record_delete_failed id={} error={}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete record: " + e.getMessage());
        }

        return redirectToRecordsFor(domain);
    }
}
