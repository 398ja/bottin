package xyz.tcheeric.bottin.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Test application for E2E tests.
 * Note: Admin module is excluded as it has conflicting security config.
 * E2E tests focus on the web API layer.
 *
 * <p>UserDetailsServiceAutoConfiguration is excluded to prevent Spring Boot
 * from overriding the UserDetailsService. The SecurityConfig from bottin-web
 * is excluded via ComponentScan filter to prevent duplicate security chains.
 * TestSecurityConfig provides the security configuration for E2E tests.</p>
 */
@SpringBootApplication(exclude = {
        UserDetailsServiceAutoConfiguration.class,
        xyz.tcheeric.bottin.starter.BottinAutoConfiguration.class
})
@ComponentScan(
        basePackages = {
                "xyz.tcheeric.bottin.core",
                "xyz.tcheeric.bottin.persistence",
                "xyz.tcheeric.bottin.service",
                "xyz.tcheeric.bottin.verification",
                "xyz.tcheeric.bottin.web.controller",
                "xyz.tcheeric.bottin.web.dto",
                "xyz.tcheeric.bottin.web.ratelimit",
                "xyz.tcheeric.bottin.starter",
                "xyz.tcheeric.bottin.e2e"
        }
)
@Import(TestSecurityConfig.class)
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
