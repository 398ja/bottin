package xyz.tcheeric.bottin.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for Bottin REST API.
 *
 * <p>Defines three security filter chains:
 * <ul>
 *   <li>Public endpoints (no auth): /.well-known/**, /api/v1/verify, swagger-ui</li>
 *   <li>REST API (Basic Auth): /api/**</li>
 *   <li>Admin UI (future): /admin/** with form login</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@Profile("!e2e")
public class SecurityConfig {

    @Value("${bottin.admin.username:admin}")
    private String adminUsername;

    @Value("${bottin.admin.password:#{T(java.util.UUID).randomUUID().toString()}}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "API")
                .build();

        UserDetails apiUser = User.builder()
                .username("api")
                .password(passwordEncoder.encode(adminPassword))
                .roles("API")
                .build();

        return new InMemoryUserDetailsManager(admin, apiUser);
    }

    /**
     * Public endpoints - no authentication required.
     * Highest priority (order 1).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(
                        "/.well-known/**",
                        "/api/v1/verify",
                        "/api/v1/profiles/*/reach",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/health"
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * REST API endpoints - Basic Authentication required.
     * Second priority (order 2).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/records/**").hasRole("API")
                        .requestMatchers("/api/v1/domains/**").hasRole("API")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CORS is handled by the edge proxy (nginx), not Spring. Removing
                // the Spring CORS filter avoids double-setting Access-Control-*
                // headers which browsers reject as "Multiple CORS header not allowed".
                .cors(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Default security for any other requests.
     * Lowest priority (order 3).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
