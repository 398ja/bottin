package xyz.tcheeric.bottin.api.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.tcheeric.bottin.service.SettingsService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiting service using a sliding window algorithm.
 *
 * <p>Limits requests per IP address to prevent abuse of the external verification endpoint.
 *
 * <p>The allowance is read from the admin-maintained settings on each check, so an
 * administrator changing it in the admin UI applies without restarting the API.
 * The counters themselves stay in memory: only the limit is stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final Map<String, RequestTracker> requestCounts = new ConcurrentHashMap<>();

    private final SettingsService settingsService;

    @Value("${bottin.ratelimit.cleanup-threshold:1000}")
    private int cleanupThreshold;

    /**
     * Checks if a request from the given IP should be allowed.
     *
     * @param clientIp the client IP address
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isAllowed(String clientIp) {
        cleanupIfNeeded();

        int limit = limitPerMinute();
        RequestTracker tracker = requestCounts.computeIfAbsent(clientIp, k -> new RequestTracker());
        boolean allowed = tracker.tryAcquire(limit);

        if (!allowed) {
            log.debug("rate_limit_exceeded ip={} limit={}", clientIp, limit);
        }

        return allowed;
    }

    /**
     * Returns the number of remaining requests for the given IP.
     *
     * @param clientIp the client IP address
     * @return number of remaining requests in the current window
     */
    public int getRemainingRequests(String clientIp) {
        int limit = limitPerMinute();
        RequestTracker tracker = requestCounts.get(clientIp);
        if (tracker == null) {
            return limit;
        }
        return Math.max(0, limit - tracker.getRequestCount());
    }

    /**
     * The currently configured allowance.
     *
     * <p>ponytail: read straight through on every rate-limited request rather than
     * memoised. The read is a primary-key lookup on a single-row table, and it is
     * dwarfed by the DNS and HTTP work the verification endpoint does next. Memoise
     * for ~60 seconds, matching the client's settings cache, if this ever shows up
     * in a profile.
     */
    private int limitPerMinute() {
        return settingsService.find().getRateLimitPerMinute();
    }

    /**
     * Cleans up expired entries to prevent memory leaks.
     */
    private void cleanupIfNeeded() {
        if (requestCounts.size() > cleanupThreshold) {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(2));
            requestCounts.entrySet().removeIf(entry -> entry.getValue().isExpired(cutoff));
            log.debug("rate_limit_cleanup remaining_entries={}", requestCounts.size());
        }
    }

    /**
     * Tracks request counts for a single client using a sliding window.
     */
    private static class RequestTracker {
        private int count = 0;
        private Instant windowStart = Instant.now();
        private Instant lastRequest = Instant.now();

        synchronized boolean tryAcquire(int limit) {
            Instant now = Instant.now();

            // Reset window if more than a minute has passed
            if (Duration.between(windowStart, now).toMinutes() >= 1) {
                windowStart = now;
                count = 0;
            }

            lastRequest = now;

            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }

        synchronized int getRequestCount() {
            Instant now = Instant.now();
            if (Duration.between(windowStart, now).toMinutes() >= 1) {
                return 0;
            }
            return count;
        }

        synchronized boolean isExpired(Instant cutoff) {
            return lastRequest.isBefore(cutoff);
        }
    }
}
