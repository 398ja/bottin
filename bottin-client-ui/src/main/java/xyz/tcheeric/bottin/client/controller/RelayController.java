package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/relays")
public class RelayController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRelays() {
        return ResponseEntity.ok(Map.of("relays", Collections.emptyList()));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> addRelay(@RequestBody Map<String, Object> body) {
        String url = (String) body.get("url");
        if (url == null || !url.startsWith("wss://")) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_url"));
        }
        return ResponseEntity.ok(Map.of("status", "added", "url", url));
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> updateRelay(@RequestBody Map<String, Object> body) {
        String url = (String) body.get("url");
        return ResponseEntity.ok(Map.of("status", "updated", "url", url));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> removeRelay(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        return ResponseEntity.ok(Map.of("status", "removed", "url", url));
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish() {
        return ResponseEntity.ok(Map.of(
                "status", "published",
                "event_id", "",
                "published_to", Collections.emptyList()
        ));
    }
}
