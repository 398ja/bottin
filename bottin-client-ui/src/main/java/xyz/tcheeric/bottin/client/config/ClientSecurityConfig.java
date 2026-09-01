package xyz.tcheeric.bottin.client.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.core.SessionStore;
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
     *
     * <p>The cap is read from configuration and passed explicitly, because that is the
     * only way it is honoured. nap-spring removed the constructors that defaulted it:
     * registering without one silently ignored {@code nap.max-body-bytes}, so a
     * deployment that tightened the cap ran on the default anyway and one that raised it
     * refused bodies it had itself configured to accept.
     *
     * <p>Absent {@link NapProperties} the registration is disabled rather than given a
     * default, matching {@link #napSessionFilter} and avoiding that same silence.
     */
    @Bean
    public FilterRegistrationBean<Filter> napServletFilter(
            ObjectProvider<NapProperties> napPropertiesProvider) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();

        NapProperties napProperties = napPropertiesProvider.getIfAvailable();
        if (napProperties == null) {
            return disabled(registrationBean);
        }

        registrationBean.setFilter(new NapServletFilter(NAP_COMPLETE_PATH, napProperties.maxBodyBytes()));
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
    public FilterRegistrationBean<Filter> napSessionFilter(
            ObjectProvider<SessionStore> sessionStoreProvider,
            ObjectProvider<AclResolver> aclResolverProvider,
            ObjectProvider<NapProperties> napPropertiesProvider
    ) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();

        SessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        AclResolver aclResolver = aclResolverProvider.getIfAvailable();
        NapProperties napProperties = napPropertiesProvider.getIfAvailable();
        if (sessionStore == null || aclResolver == null || napProperties == null) {
            return disabled(registrationBean);
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
     * Servlet URL patterns covering each configured prefix and everything beneath it.
     *
     * <p>Derived from {@code nap.protected-path-prefixes} rather than listed separately:
     * a hand-maintained second copy silently leaves a new endpoint outside the session
     * filter, and a handler that never sees a principal refuses every caller — including
     * the ones holding a valid session.
     */
    private String[] urlPatternsFor(List<String> protectedPathPrefixes) {
        return protectedPathPrefixes.stream()
                .flatMap(prefix -> Stream.of(prefix, prefix + "/*"))
                .toArray(String[]::new);
    }

    /**
     * Turns a registration off in a way that actually starts.
     *
     * <p>Setting {@code enabled} to false is not enough on its own. Spring Boot asks a
     * registration to describe itself before it consults whether the registration is
     * enabled, and the description is derived from the filter — so a disabled
     * registration holding no filter fails the application's start with "Filter must not
     * be null" rather than being skipped. Disabling therefore has to leave something
     * behind to describe.
     *
     * <p>Found by ClientListRelayE2ETest, which boots this application in a context where
     * nap-spring's beans are absent. Both registrations claimed in their documentation to
     * degrade here and both in fact threw, so the promise was never kept and never tested.
     */
    private static FilterRegistrationBean<Filter> disabled(FilterRegistrationBean<Filter> registrationBean) {
        registrationBean.setFilter((request, response, chain) -> chain.doFilter(request, response));
        registrationBean.setEnabled(false);
        return registrationBean;
    }
}
