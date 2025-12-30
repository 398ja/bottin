package xyz.tcheeric.bottin.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for end-to-end tests.
 * Provides full application context with PostgreSQL container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainerConfig.class)
@Testcontainers
@ActiveProfiles("e2e")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "e2e-test-password";

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }
}
