package xyz.tcheeric.bottin.client.config;

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
import java.util.List;
import java.util.stream.Stream;

@Configuration
public class ClientSecurityConfig {

    private static final String NAP_COMPLETE_PATH = "/api/v1/auth/complete";

    /**
     * Captures the raw request body of the NAP challenge-completion request.
     *
     * <p>{@link NapServletFilter} stashes the body as a request attribute that
     * {@link xyz.tcheeric.nap.spring.controller.NapAuthController} reads to verify the
     * NIP-98 payload hash. Without it, completion fails with "Request body not captured"
     * and no login can succeed. nap-spring does not auto-register this filter, so the
     * application must — the companion registration to {@link #napSessionFilter}.
     */
    @Bean
    public FilterRegistrationBean<NapServletFilter> napServletFilter() {
        FilterRegistrationBean<NapServletFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new NapServletFilter());
        registrationBean.addUrlPatterns(NAP_COMPLETE_PATH);
        registrationBean.setOrder(0);
        return registrationBean;
    }

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
        registrationBean.addUrlPatterns(urlPatternsFor(napProperties.protectedPathPrefixes()));
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

        NapProperties napProperties = napPropertiesProvider.getIfAvailable();
        boolean napPresent = sessionStoreProvider.getIfAvailable() != null
                && aclResolverProvider.getIfAvailable() != null
                && napProperties != null;
        if (!napPresent) {
            registrationBean.setEnabled(false);
            return registrationBean;
        }

        registrationBean.setFilter(new RequireNapAuthenticationFilter());
        registrationBean.addUrlPatterns(urlPatternsFor(napProperties.protectedPathPrefixes()));
        registrationBean.setOrder(2);
        return registrationBean;
    }

    /**
     * Servlet URL patterns covering each configured prefix and everything beneath it.
     *
     * <p>Derived from {@code nap.protected-path-prefixes} rather than listed separately:
     * a hand-maintained second copy silently leaves a new endpoint outside both NAP
     * filters, which reads as an unauthenticated caller rather than as a misconfiguration.
     */
    private String[] urlPatternsFor(List<String> protectedPathPrefixes) {
        return protectedPathPrefixes.stream()
                .flatMap(prefix -> Stream.of(prefix, prefix + "/*"))
                .toArray(String[]::new);
    }
}
