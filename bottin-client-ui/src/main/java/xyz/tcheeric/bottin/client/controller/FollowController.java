package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FollowController {

    @PostMapping("/follow")
    public ResponseEntity<Map<String, String>> follow(@RequestBody Map<String, String> body) {
        String pubkey = body.get("pubkey");
        if (pubkey == null || !pubkey.matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_pubkey"));
        }
        return ResponseEntity.ok(Map.of("status", "followed", "pubkey", pubkey));
    }

    @PostMapping("/unfollow")
    public ResponseEntity<Map<String, String>> unfollow(@RequestBody Map<String, String> body) {
        String pubkey = body.get("pubkey");
        return ResponseEntity.ok(Map.of("status", "unfollowed", "pubkey", pubkey));
    }

    @GetMapping("/follows")
    public ResponseEntity<Map<String, Object>> getFollows() {
        return ResponseEntity.ok(Map.of("follows", Collections.emptyList()));
    }
}
