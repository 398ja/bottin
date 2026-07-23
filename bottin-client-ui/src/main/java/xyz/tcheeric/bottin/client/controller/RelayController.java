package xyz.tcheeric.bottin.client.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        String error = validateRelayUrl(url);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(Map.of("status", "added", "url", url));
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> updateRelay(@RequestBody Map<String, Object> body) {
        String url = (String) body.get("url");
        String error = validateRelayUrl(url);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(Map.of("status", "updated", "url", url));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> removeRelay(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_url"));
        }
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

    private static String validateRelayUrl(String url) {
        if (url == null || url.isBlank()) {
            return "missing_url";
        }
        try {
            URI uri = URI.create(url);
            if (!"wss".equals(uri.getScheme())) {
                return "invalid_scheme";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "invalid_host";
            }
            if (isPrivateHost(host)) {
                return "host_not_allowed";
            }
            return null;
        } catch (Exception e) {
            return "invalid_url";
        }
    }

    private static final List<String> BLOCKED_HOSTS = List.of(
            "localhost", "127.0.0.1", "::1", "0.0.0.0",
            "169.254.169.254", "metadata.google.internal"
    );

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private static boolean isPrivateHost(String host) {
        String lower = host.toLowerCase();
        if (BLOCKED_HOSTS.contains(lower) || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return true;
        }
        return isInternalIpLiteral(host);
    }

    private static boolean isInternalIpLiteral(String host) {
        String candidate = host;
        if (host.startsWith("[") && host.endsWith("]")) {
            candidate = host.substring(1, host.length() - 1);
        }
        boolean looksLikeIp = IPV4_LITERAL.matcher(candidate).matches() || candidate.contains(":");
        if (!looksLikeIp) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(candidate);
            return address.isLoopbackAddress()
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }
}
