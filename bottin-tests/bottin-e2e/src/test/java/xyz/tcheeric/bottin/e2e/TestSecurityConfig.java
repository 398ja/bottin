package xyz.tcheeric.bottin.e2e;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
 * Security configuration for E2E tests.
 * Mirrors the production SecurityConfig to ensure consistent behavior.
 *
 * <p>This configuration is needed because the SecurityConfig from bottin-api
 * may conflict with Spring Security auto-configuration during tests.
 * By defining it explicitly with @Primary beans, we ensure the correct
 * security filter chains are used.</p>
 */
@Configuration
@EnableWebSecurity
public class TestSecurityConfig {

    @Value("${bottin.admin.username:admin}")
    private String adminUsername;

    @Value("${bottin.admin.password:e2e-test-password}")
    private String adminPassword;

    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public UserDetailsService testUserDetailsService(PasswordEncoder passwordEncoder) {
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

    @Bean
    @Primary
    public DaoAuthenticationProvider testAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Primary
    public AuthenticationManager testAuthenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Public endpoints - no authentication required.
     * Highest priority (order 1).
     * Note: /error is included to allow error pages for public endpoints.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain testPublicFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(
                        "/.well-known/**",
                        "/api/v1/verify",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/error"
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
    public SecurityFilterChain testApiFilterChain(HttpSecurity http) throws Exception {
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
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * Default security for any other requests.
     * Lowest priority (order 3).
     */
    @Bean
    @Order(3)
    public SecurityFilterChain testDefaultFilterChain(HttpSecurity http) throws Exception {
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
