package xyz.tcheeric.bottin.client.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.server.SessionStore;
import xyz.tcheeric.nap.spring.config.NapProperties;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.time.Duration;

@Configuration
public class ClientSecurityConfig {

    private static final String[] PROTECTED_URL_PATTERNS = {
            "/api/v1/follow/*", "/api/v1/block/*", "/api/v1/relays/*",
            "/api/v1/publish-contact-list"
    };

    /**
     * Registers the NAP session filter over the protected client API paths.
     *
     * <p>The NAP server beans ({@link SessionStore}, {@link AclResolver},
     * {@link NapProperties}) are supplied by nap-spring's auto-configuration, which is
     * registered after this user configuration. Depending on them via
     * {@code @ConditionalOnBean} would evaluate before they exist and silently drop the
     * filter, leaving every protected endpoint unguarded. They are therefore injected as
     * optional {@link ObjectProvider}s: when nap-spring is present the filter is wired to
     * the protected prefixes, and when it is absent (e.g. a {@code @WebMvcTest} slice) the
     * registration is disabled instead of failing.
     */
    @Bean
    public FilterRegistrationBean<NapSessionFilter> napSessionFilter(
            ObjectProvider<SessionStore> sessionStoreProvider,
            ObjectProvider<AclResolver> aclResolverProvider,
            ObjectProvider<NapProperties> napPropertiesProvider
    ) {
        FilterRegistrationBean<NapSessionFilter> registrationBean = new FilterRegistrationBean<>();

        SessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        AclResolver aclResolver = aclResolverProvider.getIfAvailable();
        NapProperties napProperties = napPropertiesProvider.getIfAvailable();
        if (sessionStore == null || aclResolver == null || napProperties == null) {
            registrationBean.setEnabled(false);
            return registrationBean;
        }

        registrationBean.setFilter(new NapSessionFilter(
                sessionStore,
                aclResolver,
                napProperties.cookie().name(),
                napProperties.protectedPathPrefixes(),
                Duration.ofSeconds(napProperties.aclRefreshIntervalSeconds())
        ));
        registrationBean.addUrlPatterns(PROTECTED_URL_PATTERNS);
        registrationBean.setOrder(1);
        return registrationBean;
    }

    /**
     * Requires an authenticated NAP principal on the protected client API paths.
     *
     * <p>{@link NapSessionFilter} establishes the principal but never rejects anonymous
     * callers, so this filter runs immediately after it (order 2) and returns {@code 401}
     * when no session was established. It is only registered when nap-spring is present so
     * that {@code @WebMvcTest} slices — which do not load the NAP session filter and would
     * therefore never populate a principal — are not locked out.
     */
    @Bean
    public FilterRegistrationBean<RequireNapAuthenticationFilter> requireNapAuthenticationFilter(
            ObjectProvider<SessionStore> sessionStoreProvider,
            ObjectProvider<AclResolver> aclResolverProvider,
            ObjectProvider<NapProperties> napPropertiesProvider
    ) {
        FilterRegistrationBean<RequireNapAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();

        boolean napPresent = sessionStoreProvider.getIfAvailable() != null
                && aclResolverProvider.getIfAvailable() != null
                && napPropertiesProvider.getIfAvailable() != null;
        if (!napPresent) {
            registrationBean.setEnabled(false);
            return registrationBean;
        }

        registrationBean.setFilter(new RequireNapAuthenticationFilter());
        registrationBean.addUrlPatterns(PROTECTED_URL_PATTERNS);
        registrationBean.setOrder(2);
        return registrationBean;
    }
}
