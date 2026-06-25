package xyz.tcheeric.bottin.web.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.core.exception.ReachNotAvailableException;
import xyz.tcheeric.bottin.core.reach.ProfileReach;
import xyz.tcheeric.bottin.core.reach.ReachQueryService;
import xyz.tcheeric.bottin.web.dto.ProfileReachResponse;
import xyz.tcheeric.bottin.web.ratelimit.RateLimitService;

/**
 * REST controller serving precomputed profile reach statistics.
 *
 * <p>Reach figures are produced by a scheduled background calculation and served
 * from storage; this endpoint never computes on demand. Public, rate-limited.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Profile Stats", description = "Profile reach (follower count) statistics")
public class ProfileStatsController {

    private final ReachQueryService reachQueryService;
    private final RateLimitService rateLimitService;

    @GetMapping("/{identifier}/reach")
    @Operation(
            summary = "Get the stored reach for a profile",
            description = "Returns the most recently calculated reach (distinct follower count) for the "
                    + "profile identified by its npub (bech32) or 64-character hex public key, regardless "
                    + "of the figure's age. Public endpoint; rate limited per client."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reach figure found and returned"),
            @ApiResponse(responseCode = "400", description = "Malformed or unsupported identifier"),
            @ApiResponse(responseCode = "404", description = "No reach figure available for this profile"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ProfileReachResponse> getReach(
            @Parameter(description = "Profile npub (bech32) or 64-character hex pubkey", required = true)
            @PathVariable String identifier,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        log.debug("api_profile_reach identifier={} clientIp={}", identifier, clientIp);

        if (!rateLimitService.isAllowed(clientIp)) {
            log.warn("api_profile_reach_rate_limited clientIp={}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        ProfileReach reach = reachQueryService.findReach(identifier)
                .orElseThrow(() -> new ReachNotAvailableException(identifier));

        return ResponseEntity.ok(ProfileReachResponse.from(reach));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
