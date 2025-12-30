package xyz.tcheeric.bottin.admin;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test configuration for bottin-admin-ui tests.
 * Provides a minimal Spring Boot context for testing admin controllers.
 * Only scans the admin package to avoid loading JPA dependencies.
 */
@SpringBootApplication(scanBasePackages = "xyz.tcheeric.bottin.admin")
public class TestAdminConfiguration {
}
