package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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

    @GetMapping("/api/v1/backup/export")
    public ResponseEntity<byte[]> exportBackup() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/backup/restore")
    public ResponseEntity<Map<String, String>> restoreBackup(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("status", "uploaded"));
    }
}
