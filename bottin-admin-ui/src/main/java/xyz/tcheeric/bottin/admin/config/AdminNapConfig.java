package xyz.tcheeric.bottin.admin.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.server.SessionStore;
import xyz.tcheeric.nap.spring.config.NapProperties;
import xyz.tcheeric.nap.spring.filter.NapServletFilter;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.time.Duration;

/**
 * Wires NAP into the admin dashboard.
 *
 * <p>Mirrors {@code ClientSecurityConfig} in bottin-client-ui, including its
 * treatment of the nap-spring beans as optional providers, and differs from it
 * in one respect: an unauthenticated browser is redirected to the sign-in page
 * rather than answered 401. See {@link RequireAdminSessionFilter}.
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

    private static final String SIGN_IN_PATH = "/admin/login";

    private static final String ADMIN_PREFIX = "/admin";

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
        registration.setOrder(0);
        return registration;
    }

    /**
     * Establishes the administrator principal from the session cookie.
     *
     * <p>The nap-spring beans are injected as optional providers rather than
     * depended on with {@code @ConditionalOnBean}: that condition would be
     * evaluated before the auto-configuration registers them and would silently
     * drop the filter, leaving the dashboard unguarded. Absent them — in a
     * {@code @WebMvcTest} slice, for instance — the registration is disabled
     * instead of failing.
     */
    @Bean
    public FilterRegistrationBean<NapSessionFilter> adminNapSessionFilter(
            ObjectProvider<SessionStore> sessionStoreProvider,
            ObjectProvider<AclResolver> aclResolverProvider,
            ObjectProvider<NapProperties> napPropertiesProvider
    ) {
        FilterRegistrationBean<NapSessionFilter> registration = new FilterRegistrationBean<>();

        SessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        AclResolver aclResolver = aclResolverProvider.getIfAvailable();
        NapProperties napProperties = napPropertiesProvider.getIfAvailable();
        if (sessionStore == null || aclResolver == null || napProperties == null) {
            registration.setEnabled(false);
            return registration;
        }

        registration.setFilter(new NapSessionFilter(
                sessionStore,
                aclResolver,
                napProperties.cookie().name(),
                napProperties.protectedPathPrefixes(),
                Duration.ofSeconds(napProperties.aclRefreshIntervalSeconds())
        ));
        registration.addUrlPatterns(ADMIN_PREFIX, ADMIN_PREFIX + "/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * Turns anonymous callers away from the admin surface.
     *
     * <p>Registered over the whole admin prefix rather than per route, so a page
     * added later without a permission annotation still requires a session.
     */
    @Bean
    public FilterRegistrationBean<RequireAdminSessionFilter> requireAdminSessionFilter(
            ObjectProvider<SessionStore> sessionStoreProvider,
            ObjectProvider<AclResolver> aclResolverProvider,
            ObjectProvider<NapProperties> napPropertiesProvider
    ) {
        FilterRegistrationBean<RequireAdminSessionFilter> registration = new FilterRegistrationBean<>();

        boolean napPresent = sessionStoreProvider.getIfAvailable() != null
                && aclResolverProvider.getIfAvailable() != null
                && napPropertiesProvider.getIfAvailable() != null;
        if (!napPresent) {
            registration.setEnabled(false);
            return registration;
        }

        registration.setFilter(new RequireAdminSessionFilter(SIGN_IN_PATH));
        registration.addUrlPatterns(ADMIN_PREFIX, ADMIN_PREFIX + "/*");
        registration.setOrder(2);
        return registration;
    }
}
