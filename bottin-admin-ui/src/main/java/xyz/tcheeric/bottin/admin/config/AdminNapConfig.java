package xyz.tcheeric.bottin.admin.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.tcheeric.nap.spring.filter.NapServletFilter;


/**
 * Wires the servlet-level halves of NAP into the admin dashboard.
 *
 * <p>The session filter is <em>not</em> here. Establishing the principal has to
 * happen inside Spring Security's chain, which discards any {@code
 * SecurityContext} set before it runs; see {@link AdminSecurityConfig}. What
 * remains here is the work that must happen ahead of the chain: capturing the
 * request body, and turning away a flood.
 *
 * <p>No {@code PermissionRegistry} bean is declared. That type exists for
 * {@code RegistryAclResolver}, which resolves roles from a store; this
 * deployment has one administrator in configuration rather than a store, and
 * {@code ConfiguredAdminAclResolver} returns the granted permissions directly in
 * its {@code AclDecision}. nap-spring's auto-configuration supplies the
 * interceptor that enforces {@code @RequiresPermission} either way. The
 * follow-up feature that adds further administrators is where a registry and a
 * store earn their place.
 */
@Configuration
public class AdminNapConfig {

    private static final String NAP_COMPLETE_PATH = "/api/v1/auth/complete";

    /**
     * Body capture must precede Spring Security's chain, which Spring Boot
     * registers at order -100, because the controller reads the captured
     * attribute.
     */
    private static final int NAP_BODY_CAPTURE_ORDER = -110;

    /** Ahead of everything, so a flood is turned away before any work is done. */
    private static final int AUTH_RATE_LIMIT_ORDER = -111;

    private static final String AUTH_PATH_PATTERN = "/api/v1/auth/*";

    /**
     * Rate limits the sign-in handshake, the dashboard's only unauthenticated
     * surface, as Principle VI requires of public endpoints.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter() {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthRateLimitFilter());
        registration.addUrlPatterns(AUTH_PATH_PATTERN);
        registration.setOrder(AUTH_RATE_LIMIT_ORDER);
        return registration;
    }

    /**
     * Captures the raw request body of the NAP challenge-completion request.
     *
     * <p>{@link NapServletFilter} stashes the body as a request attribute that
     * nap-spring's controller reads to verify the signed payload hash. Without
     * it, completion fails with "Request body not captured" and no sign-in can
     * succeed. nap-spring does not auto-register this filter, so the application
     * must.
     */
    @Bean
    public FilterRegistrationBean<NapServletFilter> adminNapServletFilter() {
        FilterRegistrationBean<NapServletFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new NapServletFilter());
        registration.addUrlPatterns(NAP_COMPLETE_PATH);
        registration.setOrder(NAP_BODY_CAPTURE_ORDER);
        return registration;
    }

}
