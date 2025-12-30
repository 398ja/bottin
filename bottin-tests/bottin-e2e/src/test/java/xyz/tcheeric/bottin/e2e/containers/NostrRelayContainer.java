package xyz.tcheeric.bottin.e2e.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Testcontainer for a Nostr relay.
 * Uses the dockurr/strfry image which is a high-performance C++ Nostr relay.
 */
public class NostrRelayContainer extends GenericContainer<NostrRelayContainer> {

    private static final Logger log = LoggerFactory.getLogger(NostrRelayContainer.class);

    private static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("dockurr/strfry:latest");
    public static final int RELAY_PORT = 7777;

    private static final String STRFRY_CONFIG = """
            db = "/app/strfry-db/"

            dbParams {
                maxreaders = 256
                mapsize = 10995116277760
            }

            relay {
                bind = "0.0.0.0"
                port = 7777
                nofiles = 0
                realIpHeader = ""
            }

            events {
                maxEventSize = 65536
                rejectEventsNewerThanSeconds = 900
                rejectEventsOlderThanSeconds = 94608000
                rejectEphemeralEventsOlderThanSeconds = 60
                ephemeralEventsLifetimeSeconds = 300
                maxNumTags = 2000
                maxTagValSize = 1024
            }
            """;

    public NostrRelayContainer() {
        this(DEFAULT_IMAGE);
    }

    public NostrRelayContainer(DockerImageName imageName) {
        super(imageName);
        withExposedPorts(RELAY_PORT);
        withLogConsumer(new Slf4jLogConsumer(log).withPrefix("relay"));
        withTmpFs(Map.of("/app/strfry-db", "rw"));

        try {
            Path configFile = Files.createTempFile("strfry", ".conf");
            Files.writeString(configFile, STRFRY_CONFIG);
            configFile.toFile().deleteOnExit();
            withCopyFileToContainer(MountableFile.forHostPath(configFile), "/etc/strfry.conf");
        } catch (IOException e) {
            log.error("Failed to create strfry config file", e);
            throw new RuntimeException("Failed to create strfry config file", e);
        }

        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));
    }

    /**
     * Gets the WebSocket URL for connecting to the relay.
     */
    public String getWsUrl() {
        return String.format("ws://%s:%d", getHost(), getMappedPort(RELAY_PORT));
    }

    /**
     * Gets the internal WebSocket URL for container-to-container communication.
     */
    public String getInternalWsUrl(String networkAlias) {
        return String.format("ws://%s:%d", networkAlias, RELAY_PORT);
    }
}
