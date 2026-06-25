package xyz.tcheeric.bottin.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Simplified test configuration with only PostgreSQL.
 * Used for E2E tests that don't require relay containers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresOnlyTestConfig {

    /**
     * PostgreSQL container for bottin database.
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("bottin_e2e")
                .withUsername("bottin_e2e")
                .withPassword("bottin_e2e");
    }
}
