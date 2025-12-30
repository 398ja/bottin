package xyz.tcheeric.bottin.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Testcontainers configuration for E2E tests.
 * Provides PostgreSQL, nsecbunkerd, and strfry relay containers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    private static final Network NETWORK = Network.newNetwork();

    /**
     * PostgreSQL container for bottin database.
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("bottin_e2e")
                .withUsername("bottin_e2e")
                .withPassword("bottin_e2e")
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres");
    }

    /**
     * nsecbunkerd container for key management and signing.
     * Image from docker.398ja.xyz registry.
     */
    @Bean
    public GenericContainer<?> nsecbunkerdContainer() {
        return new GenericContainer<>(DockerImageName.parse("docker.398ja.xyz/nsecbunkerd:latest"))
                .withNetwork(NETWORK)
                .withNetworkAliases("nsecbunkerd")
                .withExposedPorts(5000)
                .withEnv("NSECBUNKER_LOG_LEVEL", "debug")
                .waitingFor(Wait.forHttp("/health")
                        .forPort(5000)
                        .withStartupTimeout(Duration.ofSeconds(60)));
    }

    /**
     * strfry relay container for Nostr relay functionality.
     * Used for NIP-46 remote signing communication.
     * Uses dockurr/strfry image from Docker Hub.
     */
    @Bean
    public GenericContainer<?> strfryContainer() {
        return new GenericContainer<>(DockerImageName.parse("dockurr/strfry:latest"))
                .withNetwork(NETWORK)
                .withNetworkAliases("relay")
                .withExposedPorts(7777)
                .waitingFor(Wait.forListeningPort()
                        .withStartupTimeout(Duration.ofSeconds(30)));
    }

    /**
     * Returns the shared network for all containers.
     */
    @Bean
    public Network testNetwork() {
        return NETWORK;
    }
}
