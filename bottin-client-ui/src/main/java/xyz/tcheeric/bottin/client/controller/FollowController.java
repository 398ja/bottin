package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.tcheeric.nap.spring.annotation.RequiresSession;

import java.util.Collections;
import java.util.Map;

/**
 * Who the signed-in user follows. Every route here acts on, or reveals, one
 * person's follow list, so all of them require that person to be signed in.
 */
@RequiresSession
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
        if (pubkey == null || !pubkey.matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_pubkey"));
        }
        return ResponseEntity.ok(Map.of("status", "unfollowed", "pubkey", pubkey));
    }

    @GetMapping("/follows")
    public ResponseEntity<Map<String, Object>> getFollows() {
        return ResponseEntity.ok(Map.of("follows", Collections.emptyList()));
    }
}
