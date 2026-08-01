package xyz.tcheeric.bottin.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.verification.ExternalNip05VerificationResult;
import xyz.tcheeric.bottin.verification.ExternalNip05Verifier;
import xyz.tcheeric.bottin.api.dto.ExternalVerificationResponse;
import xyz.tcheeric.bottin.api.ratelimit.RateLimitService;
import xyz.tcheeric.bottin.api.util.ClientIp;

/**
 * REST controller for verifying external NIP-05 identifiers.
 *
 * <p>Provides an endpoint to verify NIP-05 identifiers from any domain
 * by fetching and parsing the remote {@code .well-known/nostr.json} file.
 */
@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "External Verification", description = "Verify external NIP-05 identifiers")
public class ExternalVerificationController {

    private final ExternalNip05Verifier externalNip05Verifier;
    private final RateLimitService rateLimitService;

    @GetMapping
    @Operation(
            summary = "Verify external NIP-05",
            description = "Verifies an external NIP-05 identifier by fetching the remote nostr.json file. " +
                    "Results are cached for 5 minutes by default to reduce load on external servers. " +
                    "Rate limited to 30 requests per minute per IP."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification completed (check 'valid' field for result)"),
            @ApiResponse(responseCode = "400", description = "Invalid NIP-05 format"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ExternalVerificationResponse> verify(
            @Parameter(description = "NIP-05 identifier to verify (e.g., alice@example.com)", required = true)
            @RequestParam String nip05,
            @Parameter(description = "Skip cache and fetch fresh data")
            @RequestParam(required = false, defaultValue = "false") boolean noCache,
            HttpServletRequest request) {

        String clientIp = ClientIp.resolve(request);
        log.debug("api_external_verify nip05={} noCache={} clientIp={}", nip05, noCache, clientIp);

        if (!rateLimitService.isAllowed(clientIp)) {
            log.warn("api_external_verify_rate_limited clientIp={}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        ExternalNip05VerificationResult result;
        if (noCache) {
            result = externalNip05Verifier.verifyNoCache(nip05);
        } else {
            result = externalNip05Verifier.verify(nip05);
        }

        return ResponseEntity.ok(ExternalVerificationResponse.from(result));
    }
}
