package xyz.tcheeric.bottin.client.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.client.dto.RegistrationRequest;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationException;
import xyz.tcheeric.bottin.client.service.DirectoryRegistrationService;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.util.Map;

/**
 * Registers the handle chosen during onboarding with the bottin directory, which
 * is what makes the new user visible in the admin UI and resolvable over NIP-05.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    private static final String USERNAME_PATTERN = "[a-z0-9_-]{1,64}";

    private final DirectoryRegistrationService registrationService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegistrationRequest request) {
        String pubkey = authenticatedPubkey();
        if (pubkey == null) {
            return error(HttpStatus.UNAUTHORIZED, "NAP_SESSION_REQUIRED");
        }

        String username = request.username() == null ? "" : request.username().trim().toLowerCase();
        if (!username.matches(USERNAME_PATTERN)) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_USERNAME");
        }

        try {
            String nip05 = registrationService.register(username, pubkey, request.relays());
            return ResponseEntity.ok(Map.of("status", "registered", "nip05", nip05));
        } catch (DirectoryRegistrationException e) {
            log.warn("handle_registration_failed username={} pubkey={} code={} error={}",
                    username, pubkey, e.getErrorCode(), e.getMessage());
            return error(statusFor(e), e.getErrorCode());
        }
    }

    /**
     * The pubkey of the NAP session that signed this request, or {@code null}
     * when the request arrived without one.
     */
    private String authenticatedPubkey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof NapSessionFilter.NapAuthenticationToken napToken
                ? napToken.getPubkey()
                : null;
    }

    private HttpStatus statusFor(DirectoryRegistrationException e) {
        return switch (e.getErrorCode()) {
            case DirectoryRegistrationException.USERNAME_TAKEN -> HttpStatus.CONFLICT;
            case DirectoryRegistrationException.DOMAIN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(Map.of("status", "error", "code", code));
    }
}
