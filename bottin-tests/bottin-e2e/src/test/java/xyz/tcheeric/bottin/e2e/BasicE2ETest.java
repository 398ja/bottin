package xyz.tcheeric.bottin.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for basic E2E tests that only require PostgreSQL.
 * Does not include relay containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresOnlyTestConfig.class)
@Testcontainers
@ActiveProfiles("e2e")
public abstract class BasicE2ETest {

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
