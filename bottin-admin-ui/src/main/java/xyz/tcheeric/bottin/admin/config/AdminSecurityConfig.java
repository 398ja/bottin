package xyz.tcheeric.bottin.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.core.SessionStore;
import xyz.tcheeric.nap.spring.config.NapProperties;
import xyz.tcheeric.nap.spring.filter.NapSessionFilter;

import java.time.Duration;
import java.util.Optional;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * Security configuration for the admin dashboard.
 *
 * <p>There is no username, no password, and no user store. An administrator is
 * whoever proves control of the configured Nostr key, and
 * {@code ConfiguredAdminAclResolver} decides whether it is the administrator's.
 *
 * <p>{@link NapSessionFilter} runs <em>inside</em> this chain, in a window with a
 * wall on each side. Both were found by an administrator being refused with a
 * valid key.
 *
 * <p>It must come <b>after</b> {@code SecurityContextHolderFilter}, which begins
 * by replacing the {@code SecurityContext} with one loaded from its own
 * repository. Registered as a plain servlet filter ahead of the chain, NAP
 * established the principal and Spring Security then discarded it: sign-in
 * succeeded and every page bounced back to the sign-in form.
 *
 * <p>It must come <b>before</b> {@link AnonymousAuthenticationFilter}, which
 * populates the context with an anonymous token. {@code NapSessionFilter} steps
 * aside whenever an authentication is already present and reports itself
 * authenticated — which an anonymous token does — so behind it the session
 * cookie is never read at all.
 *
 * <p>In that window the NAP principal is what {@code .anyRequest().authenticated()},
 * the {@code @RequiresPermission} interceptor, and the {@code sec:} Thymeleaf
 * integration in the layout all read.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private static final String SIGN_IN_PATH = "/admin/login";

    private final ObjectProvider<SessionStore> sessionStoreProvider;

    private final ObjectProvider<AclResolver> aclResolverProvider;

    private final ObjectProvider<NapProperties> napPropertiesProvider;

    /**
     * The NAP session filter, or empty where nap-spring's beans are absent — a
     * {@code @WebMvcTest} slice, for instance. Built here rather than injected
     * because Spring Boot registers any {@code Filter} bean into the servlet
     * chain, which is the placement this class exists to avoid.
     */
    private Optional<NapSessionFilter> napSessionFilter() {
        SessionStore sessionStore = sessionStoreProvider.getIfAvailable();
        AclResolver aclResolver = aclResolverProvider.getIfAvailable();
        NapProperties properties = napPropertiesProvider.getIfAvailable();

        if (sessionStore == null || aclResolver == null || properties == null) {
            return Optional.empty();
        }

        return Optional.of(new NapSessionFilter(
                sessionStore,
                aclResolver,
                properties.cookie().name(),
                properties.protectedPathPrefixes(),
                Duration.ofSeconds(properties.aclRefreshIntervalSeconds())
        ));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        napSessionFilter().ifPresent(filter ->
                http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));

        // Before authorization, so it is inside the security chain and the
        // principal is still in the context while the chain unwinds. Outside it,
        // every refusal would be recorded as anonymous.
        http.addFilterBefore(new RefusedAdminRequestLogFilter(), AuthorizationFilter.class);

        return http
                .securityMatcher("/admin/**", "/api/v1/auth/**")
                .authorizeHttpRequests(auth -> auth
                        // The sign-in page must be reachable by someone who is not
                        // yet signed in, and the handshake must answer before
                        // anyone is authenticated at all.
                        .requestMatchers(SIGN_IN_PATH, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/admin/css/**", "/admin/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                // The handshake endpoints are exempt from CSRF. They are POSTs
                // that no session authenticates: what authorises them is a
                // signed challenge, which a cross-site attacker cannot produce
                // because it needs the administrator's private key. Requiring a
                // CSRF token as well would only mean no administrator can sign
                // in, since the token cannot be obtained before the session it
                // is meant to protect exists. Every other admin POST keeps it.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/auth/**"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(adminAuthenticationEntryPoint()))
                .build();
    }

    /**
     * Sends a browser to the sign-in page and everything else a 401.
     *
     * <p>A bare 401 in a browser is a blank error page rather than an invitation
     * to sign in; a fetch, on the other hand, has nothing to do with a redirect
     * to an HTML form. The two are told apart by whether the request accepts
     * HTML.
     */
    private DelegatingAuthenticationEntryPoint adminAuthenticationEntryPoint() {
        MediaTypeRequestMatcher browserRequest = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        browserRequest.setUseEquals(false);

        LinkedHashMap<RequestMatcher, org.springframework.security.web.AuthenticationEntryPoint> entryPoints =
                new LinkedHashMap<>();
        entryPoints.put(browserRequest, new LoginUrlAuthenticationEntryPoint(SIGN_IN_PATH));

        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return entryPoint;
    }
}
