package xyz.tcheeric.bottin.client.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects unauthenticated requests to the protected client API paths.
 *
 * <p>{@link xyz.tcheeric.nap.spring.filter.NapSessionFilter} validates the NAP session
 * cookie and, when it is valid, stores an {@link Authentication} in the
 * {@link SecurityContextHolder}. It does not, however, reject requests that arrive
 * without a session — it simply lets them through as anonymous. This filter is mapped
 * over the same protected paths and runs immediately after it, returning {@code 401}
 * when no authenticated principal was established so anonymous callers cannot reach
 * the follow/block/relay/backup mutations.
 */
public class RequireNapAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (hasAuthenticatedPrincipal()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"error\",\"code\":\"NAP_SESSION_REQUIRED\"}");
    }

    private boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
