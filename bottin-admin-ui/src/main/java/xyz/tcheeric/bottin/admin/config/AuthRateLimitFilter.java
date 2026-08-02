package xyz.tcheeric.bottin.admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits how often one client address may start the sign-in handshake.
 *
 * <p>The handshake endpoints are unauthenticated by necessity — they must answer
 * before anybody has a session — and they do signature verification on demand.
 * Principle VI requires public endpoints to be rate limited, and this is the
 * only unauthenticated surface the dashboard has.
 *
 * <p>Deliberately not reusing {@code bottin-api}'s {@code RateLimitService}: that
 * one now reads its allowance from the settings row through {@code
 * SettingsService}, and a presentation module should not acquire a service and
 * database dependency to enforce a fixed local limit.
 *
 * <p>ponytail: a fixed window with a map keyed on client address, swept when it
 * grows. A single administrator signing in does not need a sliding window or a
 * shared store; if the dashboard ever runs behind a load balancer with several
 * instances, each will hold its own counters and the effective limit multiplies
 * by the instance count.
 */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Generous next to a human signing in, tight next to somebody working
     * through candidate keys.
     */
    private static final int MAX_ATTEMPTS_PER_WINDOW = 20;

    private static final int SWEEP_THRESHOLD = 1000;

    private final Map<String, Window> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String client = clientAddress(request);
        if (exceededLimit(client)) {
            log.warn("admin_signin_rate_limited client_ip={}", client);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean exceededLimit(String client) {
        sweepIfLarge();

        Window window = attempts.compute(client, (key, existing) ->
                existing == null || existing.hasExpired() ? new Window() : existing);

        return window.count.incrementAndGet() > MAX_ATTEMPTS_PER_WINDOW;
    }

    /**
     * The address of the peer, not a header it supplied. The dashboard should
     * not be behind a proxy that rewrites this, and honouring a header here
     * would let a caller mint a fresh identity per request and walk past the
     * limit — the same bypass closed for the API in a62d311.
     */
    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void sweepIfLarge() {
        if (attempts.size() > SWEEP_THRESHOLD) {
            attempts.values().removeIf(Window::hasExpired);
        }
    }

    private static final class Window {
        private final Instant startedAt = Instant.now();
        private final AtomicInteger count = new AtomicInteger();

        private boolean hasExpired() {
            return Instant.now().isAfter(startedAt.plus(WINDOW));
        }
    }
}
