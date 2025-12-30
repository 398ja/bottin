package xyz.tcheeric.bottin.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.tcheeric.bottin.admin.service.AdminDashboardService;

/**
 * Controller for admin dashboard.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping
    public String redirectToDashboard() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        log.debug("admin_dashboard_accessed");

        var stats = dashboardService.getDashboardStats();
        var recentRecords = dashboardService.getRecentRecords(10);
        var recentDomains = dashboardService.getRecentDomains(5);
        var pendingDomains = dashboardService.getPendingVerificationDomains();

        model.addAttribute("stats", stats);
        model.addAttribute("recentRecords", recentRecords);
        model.addAttribute("recentDomains", recentDomains);
        model.addAttribute("pendingDomains", pendingDomains);

        return "admin/dashboard";
    }
}
