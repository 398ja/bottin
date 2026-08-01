package xyz.tcheeric.bottin.admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.io.IOException;

/**
 * Requires an authenticated administrator on the admin surface, sending browsers
 * to the sign-in page and everything else a 401.
 *
 * <p>{@link NapSessionFilter} establishes the principal but never rejects
 * anonymous callers, so this filter runs immediately after it.
 *
 * <p>It deliberately differs from the client's equivalent, which answers 401 for
 * everything. The client protects fetch calls made from JavaScript; the
 * dashboard protects server-rendered pages an operator navigates to, and a bare
 * 401 in a browser is a blank error page rather than an invitation to sign in.
 * The two are told apart by whether the request accepts HTML — a distinction
 * that matters only once the dashboard gains an API of its own, but which is
 * cheaper to make now than to retrofit.
 *
 * <p>It also runs across the whole admin prefix, independently of the
 * {@code @RequiresPermission} annotations on individual handlers. A route added
 * later that nobody annotates therefore degrades to "authenticated but
 * unrestricted", never to "public".
 */
@Slf4j
public class RequireAdminSessionFilter extends OncePerRequestFilter {

    private final String signInPath;

    public RequireAdminSessionFilter(String signInPath) {
        this.signInPath = signInPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isSignInPage(request) || hasAdminSession()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (expectsHtml(request)) {
            log.debug("admin_access_denied path={} outcome=redirected_to_sign_in", request.getRequestURI());
            response.sendRedirect(request.getContextPath() + signInPath);
            return;
        }

        log.debug("admin_access_denied path={} outcome=unauthorized", request.getRequestURI());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * The sign-in page is always reachable, or an unauthenticated visitor would
     * be redirected to a page that redirects them again.
     */
    private boolean isSignInPage(HttpServletRequest request) {
        return signInPath.equals(request.getRequestURI());
    }

    private boolean hasAdminSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof NapSessionFilter.NapAuthenticationToken;
    }

    /**
     * Whether this looks like a browser navigating rather than code fetching.
     * A browser asks for HTML; a fetch asks for JSON or states no preference.
     */
    private boolean expectsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
