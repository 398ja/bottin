package xyz.tcheeric.bottin.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
 * whoever proves control of the configured Nostr key: {@link AdminNapConfig}
 * registers the filters that establish that principal, and
 * {@code ConfiguredAdminAclResolver} decides whether it is the administrator's.
 *
 * <p>This chain remains as a backstop rather than as the primary guard. The NAP
 * filters run ahead of it and turn anonymous callers away first; if they were
 * ever disabled, this chain would still refuse rather than serve the dashboard
 * to anybody. It also keeps CSRF protection and the {@code sec:} Thymeleaf
 * integration the layout uses.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private static final String SIGN_IN_PATH = "/admin/login";

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
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
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(adminAuthenticationEntryPoint()))
                .build();
    }

    /**
     * Sends a browser to the sign-in page and everything else a 401.
     *
     * <p>Mirrors {@link RequireAdminSessionFilter}, which normally answers first;
     * the two must agree, or a request that slipped past the filter would get a
     * different answer here.
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
