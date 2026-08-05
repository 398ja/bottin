package xyz.tcheeric.bottin.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import xyz.tcheeric.bottin.persistence.entity.SettingsEntity;
import xyz.tcheeric.bottin.persistence.repository.SettingsRepository;

import java.time.Instant;

/**
 * Base class for end-to-end tests.
 * Provides full application context with PostgreSQL and strfry containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig.class)
@Testcontainers
@ActiveProfiles("e2e")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected GenericContainer<?> strfryContainer;

    @Autowired
    private SettingsRepository settingsRepository;

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "e2e-test-password";

    /**
     * Allowance generous enough that a test making many calls in quick
     * succession is not rate limited by its own fixture.
     */
    private static final int E2E_RATE_LIMIT_PER_MINUTE = 100;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    /**
     * Seeds the settings row that the V4 migration would normally insert.
     *
     * <p>These tests build their schema with Hibernate rather than Flyway
     * ({@code spring.flyway.enabled=false}, {@code ddl-auto=create-drop}), so the
     * migration never runs and the row is absent. The rate limiter reads its
     * allowance from that row and raises rather than inventing a default, so
     * without this every rate-limited endpoint would answer 500.
     */
    @BeforeEach
    void seedDeploymentSettings() {
        if (settingsRepository.findById(SettingsEntity.SINGLETON_ID).isPresent()) {
            return;
        }
        settingsRepository.save(SettingsEntity.builder()
                .id(SettingsEntity.SINGLETON_ID)
                .rateLimitPerMinute(E2E_RATE_LIMIT_PER_MINUTE)
                .updatedAt(Instant.now())
                .build());
    }

    /**
     * Returns the strfry relay WebSocket URL for the running container.
     */
    protected String getRelayUrl() {
        return "ws://" + strfryContainer.getHost() + ":" + strfryContainer.getMappedPort(7777);
    }
}
