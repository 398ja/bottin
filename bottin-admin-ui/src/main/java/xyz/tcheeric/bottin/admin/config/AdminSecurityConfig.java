package xyz.tcheeric.bottin.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the admin dashboard.
 * Uses form-based authentication with session management.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    @Value("${bottin.admin.username:admin}")
    private String adminUsername;

    @Value("${BOTTIN_ADMIN_PASSWORD:admin}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/admin/**", "/login", "/logout")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/css/**", "/admin/js/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/admin/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .build();
    }

    @Bean
    public UserDetailsService adminUserDetailsService(PasswordEncoder passwordEncoder) {
        // Admin user configured via environment variables
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }
}
