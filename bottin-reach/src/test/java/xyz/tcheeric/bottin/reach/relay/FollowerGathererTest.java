package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests the follower gatherer's event parsing, cross-relay de-duplication, and
 * complete/partial/anyData flag logic, using a stubbed relay client.
 */
@ExtendWith(MockitoExtension.class)
class FollowerGathererTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TARGET = "aaaa000000000000000000000000000000000000000000000000000000000000";
    private static final String FOLLOWER = "bbbb000000000000000000000000000000000000000000000000000000000000";

    @Mock
    private RelayQueryClient relayQueryClient;

    /** A single relay reaching EOSE with one follower yields reach 1, complete and with data. */
    @Test
    void shouldCountFollowerFromSingleRelay() throws Exception {
        // Arrange
        FollowerGatherer gatherer = new FollowerGatherer(relayQueryClient);
        when(relayQueryClient.query(eq("wss://a"), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(kind3("e1", FOLLOWER, 100, TARGET)), true));

        // Act
        GatherResult result = gatherer.gather(TARGET, List.of("wss://a"), 5000, 10);

        // Assert
        assertThat(result.reachCount()).isEqualTo(1);
        assertThat(result.complete()).isTrue();
        assertThat(result.anyData()).isTrue();
    }

    /** The same follower event seen on two relays is counted once (global de-duplication). */
    @Test
    void shouldDeduplicateSameFollowerAcrossRelays() throws Exception {
        // Arrange
        FollowerGatherer gatherer = new FollowerGatherer(relayQueryClient);
        when(relayQueryClient.query(eq("wss://a"), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(kind3("e1", FOLLOWER, 100, TARGET)), true));
        when(relayQueryClient.query(eq("wss://b"), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(kind3("e1", FOLLOWER, 100, TARGET)), true));

        // Act
        GatherResult result = gatherer.gather(TARGET, List.of("wss://a", "wss://b"), 5000, 10);

        // Assert
        assertThat(result.reachCount()).isEqualTo(1);
        assertThat(result.complete()).isTrue();
    }

    /** A relay that does not reach EOSE marks the gather as partial (not complete). */
    @Test
    void shouldMarkPartialWhenRelayDidNotReachEose() throws Exception {
        // Arrange
        FollowerGatherer gatherer = new FollowerGatherer(relayQueryClient);
        when(relayQueryClient.query(eq("wss://a"), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(kind3("e1", FOLLOWER, 100, TARGET)), false));

        // Act
        GatherResult result = gatherer.gather(TARGET, List.of("wss://a"), 5000, 10);

        // Assert
        assertThat(result.reachCount()).isEqualTo(1);
        assertThat(result.complete()).isFalse();
        assertThat(result.anyData()).isTrue();
    }

    /** When one relay fails but another responds, the figure is partial but still has data. */
    @Test
    void shouldRemainPartialWhenOneRelayFails() throws Exception {
        // Arrange
        FollowerGatherer gatherer = new FollowerGatherer(relayQueryClient);
        when(relayQueryClient.query(eq("wss://a"), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(kind3("e1", FOLLOWER, 100, TARGET)), true));
        when(relayQueryClient.query(eq("wss://b"), any(), anyLong()))
                .thenThrow(new RuntimeException("relay unreachable"));

        // Act
        GatherResult result = gatherer.gather(TARGET, List.of("wss://a", "wss://b"), 5000, 10);

        // Assert
        assertThat(result.reachCount()).isEqualTo(1);
        assertThat(result.complete()).isFalse();
        assertThat(result.anyData()).isTrue();
    }

    /** When every relay fails, no data was gathered (so a prior figure must be retained). */
    @Test
    void shouldReportNoDataWhenAllRelaysFail() throws Exception {
        // Arrange
        FollowerGatherer gatherer = new FollowerGatherer(relayQueryClient);
        when(relayQueryClient.query(any(), any(), anyLong()))
                .thenThrow(new RuntimeException("relay unreachable"));

        // Act
        GatherResult result = gatherer.gather(TARGET, List.of("wss://a", "wss://b"), 5000, 10);

        // Assert
        assertThat(result.anyData()).isFalse();
        assertThat(result.reachCount()).isZero();
    }

    private static JsonNode kind3(String id, String author, long createdAt, String taggedTarget) throws Exception {
        String json = "{\"id\":\"" + id + "\",\"pubkey\":\"" + author + "\",\"created_at\":" + createdAt
                + ",\"kind\":3,\"tags\":[[\"p\",\"" + taggedTarget + "\"]],\"content\":\"\"}";
        return MAPPER.readTree(json);
    }
}
