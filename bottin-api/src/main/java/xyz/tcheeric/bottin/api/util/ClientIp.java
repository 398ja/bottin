package xyz.tcheeric.bottin.api.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for a request. Shared by all rate-limited
 * endpoints so they interpret the caller's address consistently.
 *
 * <p>Proxy headers are deliberately not read here. {@code X-Forwarded-For} is
 * attacker-controlled on any request that did not come through a trusted proxy, and
 * trusting it let a caller mint a fresh identity per request and walk past every
 * rate limit. Tomcat's {@code RemoteIpValve} — enabled by
 * {@code server.forward-headers-strategy=native} — applies the header only when the
 * immediate peer is a configured internal proxy, and rewrites the remote address
 * accordingly, so the trust decision lives in one place with the deployment's proxy
 * list rather than in each endpoint.
 */
public final class ClientIp {

    private ClientIp() {
    }

    /**
     * Returns the originating client IP: the request's remote address, already
     * resolved from the proxy chain by the container where that chain is trusted.
     */
    public static String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
