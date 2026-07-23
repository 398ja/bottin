package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the backup and restore pages. Backup export and restore run entirely in
 * the browser against the encrypted identity held in {@code localStorage}, so there
 * are no server-side backup endpoints — the server cannot read the client's key.
 */
@Controller
public class BackupController {

    @GetMapping("/restore")
    public String restorePage(Model model) {
        model.addAttribute("title", "Restore Backup");
        model.addAttribute("content", "backup");
        return "layout";
    }

    @GetMapping("/settings/backup")
    public String backupPage(Model model) {
        model.addAttribute("title", "Backup");
        model.addAttribute("content", "backup");
        return "layout";
    }
}
