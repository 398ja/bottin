package xyz.tcheeric.bottin.web.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Bottin Web REST API service.
 * Provides NIP-05 identity resolution and management endpoints.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "xyz.tcheeric.bottin.core",
        "xyz.tcheeric.bottin.persistence",
        "xyz.tcheeric.bottin.service",
        "xyz.tcheeric.bottin.verification",
        "xyz.tcheeric.bottin.reach",
        "xyz.tcheeric.bottin.web"
})
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
public class BottinWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BottinWebApplication.class, args);
    }
}
