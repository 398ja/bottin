package xyz.tcheeric.bottin.web.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for a request, honouring the
 * {@code X-Forwarded-For} header set by the edge proxy. Shared by all rate-limited
 * endpoints so they interpret proxy headers consistently.
 */
public final class ClientIp {

    private ClientIp() {
    }

    /**
     * Returns the originating client IP: the first entry of {@code X-Forwarded-For}
     * when present, otherwise the request's remote address.
     */
    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
