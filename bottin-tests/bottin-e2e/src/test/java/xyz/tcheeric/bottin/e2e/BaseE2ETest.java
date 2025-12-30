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

/**
 * Base class for end-to-end tests.
 * Provides full application context with PostgreSQL, nsecbunkerd, and strfry containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig.class)
@Testcontainers
@ActiveProfiles("e2e")
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected GenericContainer<?> nsecbunkerdContainer;

    @Autowired
    protected GenericContainer<?> strfryContainer;

    protected static final String ADMIN_USER = "admin";
    protected static final String ADMIN_PASSWORD = "e2e-test-password";

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    /**
     * Returns the nsecbunkerd URL for the running container.
     */
    protected String getNsecbunkerdUrl() {
        return "http://" + nsecbunkerdContainer.getHost() + ":" + nsecbunkerdContainer.getMappedPort(5000);
    }

    /**
     * Returns the strfry relay WebSocket URL for the running container.
     */
    protected String getRelayUrl() {
        return "ws://" + strfryContainer.getHost() + ":" + strfryContainer.getMappedPort(7777);
    }
}
