package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class BlockController {

    @PostMapping("/block")
    public ResponseEntity<Map<String, String>> block(@RequestBody Map<String, String> body) {
        String pubkey = body.get("pubkey");
        if (pubkey == null || !pubkey.matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_pubkey"));
        }
        return ResponseEntity.ok(Map.of("status", "blocked", "pubkey", pubkey));
    }

    @PostMapping("/unblock")
    public ResponseEntity<Map<String, String>> unblock(@RequestBody Map<String, String> body) {
        String pubkey = body.get("pubkey");
        return ResponseEntity.ok(Map.of("status", "unblocked", "pubkey", pubkey));
    }

    @GetMapping("/blocks")
    public ResponseEntity<Map<String, Object>> getBlocks() {
        return ResponseEntity.ok(Map.of("blocks", Collections.emptyList()));
    }
}
