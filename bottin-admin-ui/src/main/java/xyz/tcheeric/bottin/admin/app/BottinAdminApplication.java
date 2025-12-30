package xyz.tcheeric.bottin.admin.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main entry point for the Bottin Admin Dashboard service.
 * Provides web-based administration interface for NIP-05 registry management.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "xyz.tcheeric.bottin.core",
        "xyz.tcheeric.bottin.persistence",
        "xyz.tcheeric.bottin.service",
        "xyz.tcheeric.bottin.verification",
        "xyz.tcheeric.bottin.admin"
})
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
public class BottinAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BottinAdminApplication.class, args);
    }
}
