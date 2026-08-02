package xyz.tcheeric.bottin.admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Records admin requests refused for want of a permission.
 *
 * <p>The security log must show every rejected request, and a refused attempt to
 * change who administers the deployment is the one most worth having. Nothing
 * else logs it: nap's permission interceptor writes {@code 403} straight onto
 * the response and returns, so the controller never runs and Spring Security's
 * access-denied handling never fires — there is no {@code AccessDeniedException}
 * to handle.
 *
 * <p>It therefore observes the outcome rather than the decision, logging on the
 * way out when the status is {@code 403}. Placed inside the security chain and
 * before authorization, so the principal is still in the context as the chain
 * unwinds; outside it, the context has already been cleared and every refusal
 * would be logged as anonymous.
 *
 * <p>This filter only reports. It refuses nothing, so a fault here cannot admit
 * anybody.
 */
@Slf4j
public class RefusedAdminRequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        if (response.getStatus() != HttpStatus.FORBIDDEN.value()) {
            return;
        }

        log.warn("administrator_change_rejected reason=insufficient_permission method={} path={} pubkey={}",
                request.getMethod(), request.getRequestURI(), principal());
    }

    private String principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
