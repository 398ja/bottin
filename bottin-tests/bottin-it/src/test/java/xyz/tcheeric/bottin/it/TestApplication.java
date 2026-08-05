package xyz.tcheeric.bottin.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test application for integration tests.
 *
 * <p>Declares the entity and repository scanning these tests need. The services
 * they exercise come from {@code BottinAutoConfiguration}, which the starter
 * registers through {@code AutoConfiguration.imports}.
 *
 * <p>{@code xyz.tcheeric.bottin.api} is not on any scan path here, and the
 * auto-configuration no longer scans it either. It contains
 * {@code BottinApiApplication} — a second {@code @SpringBootApplication} with its
 * own {@code @EnableJpaRepositories} — and {@code SecurityConfig}, whose "any
 * request" filter chain collides with Spring Boot's default one when it is
 * registered late by an auto-configuration's component scan.
 */
@SpringBootApplication
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
