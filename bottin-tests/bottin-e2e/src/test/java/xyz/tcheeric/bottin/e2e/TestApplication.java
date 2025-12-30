package xyz.tcheeric.bottin.e2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test application for E2E tests.
 * Note: Admin module is excluded as it has conflicting security config.
 * E2E tests focus on the web API layer.
 */
@SpringBootApplication(scanBasePackages = {
        "xyz.tcheeric.bottin.core",
        "xyz.tcheeric.bottin.persistence",
        "xyz.tcheeric.bottin.service",
        "xyz.tcheeric.bottin.verification",
        "xyz.tcheeric.bottin.web",
        "xyz.tcheeric.bottin.starter",
        "xyz.tcheeric.bottin.e2e"
})
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
