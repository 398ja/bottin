package xyz.tcheeric.bottin.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.service.WellKnownJsonGenerator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Controller serving the NIP-05 well-known endpoint.
 *
 * <p>Per NIP-05 specification, this endpoint serves:
 * <ul>
 *   <li>GET /.well-known/nostr.json?name={username} - returns the pubkey for a specific username</li>
 *   <li>GET /.well-known/nostr.json - returns all records for the domain</li>
 * </ul>
 *
 * <p>The domain is determined from the Host header or a configured default domain.
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/05.md">NIP-05</a>
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
@Slf4j
public class WellKnownController {

    private static final String JSON_NIP05_CONTENT_TYPE = "application/json";
    private static final int CACHE_MAX_AGE_SECONDS = 3600;

    private final WellKnownJsonGenerator wellKnownJsonGenerator;

    @Value("${bottin.default-domain:}")
    private String defaultDomain;

    /**
     * Serves the NIP-05 nostr.json endpoint.
     *
     * <p>If the 'name' parameter is provided, returns only the record for that username.
     * If no 'name' parameter is provided, returns all enabled records for the domain.
     *
     * @param name optional username to filter by
     * @param host the Host header to determine the domain
     * @return NIP-05 compliant JSON response
     */
    @GetMapping(value = "/nostr.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getNostrJson(
            @RequestParam(value = "name", required = false) String name,
            @RequestHeader(value = "Host", required = false) String host) {

        String domain = extractDomain(host);
        log.debug("wellknown_request domain={} name={}", domain, name);

        if (domain == null || domain.isBlank()) {
            log.warn("wellknown_request_failed reason=no_domain_determined host={}", host);
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> response;
        if (name != null && !name.isBlank()) {
            Optional<Map<String, Object>> result = wellKnownJsonGenerator.generateForUsername(domain, name);
            if (result.isEmpty()) {
                log.debug("wellknown_response domain={} name={} status=not_found", domain, name);
                response = Map.of("names", Map.of());
            } else {
                response = result.get();
            }
        } else {
            response = wellKnownJsonGenerator.generateForDomain(domain);
        }

        log.debug("wellknown_response domain={} name_count={}",
                domain, ((Map<?, ?>) response.get("names")).size());

        // CORS (Access-Control-Allow-Origin) is set by the edge proxy, not here.
        // The reverse proxy is the right place for cross-cutting concerns —
        // setting it both here and at the edge produces two headers and browsers
        // reject the response with "Multiple CORS header ... not allowed".
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE_SECONDS, TimeUnit.SECONDS).cachePublic())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Extracts the domain from the Host header.
     * Falls back to the configured default domain if Host is not available.
     */
    private String extractDomain(String host) {
        if (host != null && !host.isBlank()) {
            // Remove port if present
            int portIndex = host.indexOf(':');
            if (portIndex > 0) {
                return host.substring(0, portIndex).toLowerCase();
            }
            return host.toLowerCase();
        }
        return defaultDomain != null && !defaultDomain.isBlank() ? defaultDomain.toLowerCase() : null;
    }
}
