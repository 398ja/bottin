package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests parsing of NIP-65 (kind-10002) relay-list events into relay URLs.
 */
@ExtendWith(MockitoExtension.class)
class Nip65RelayResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TARGET = "aaaa000000000000000000000000000000000000000000000000000000000000";

    @Mock
    private RelayQueryClient relayQueryClient;

    /** r-tags (with or without read/write markers) are parsed into ws(s) relay URLs; other tags ignored. */
    @Test
    void shouldParseRelayUrlsFromRTags() throws Exception {
        // Arrange
        Nip65RelayResolver resolver = new Nip65RelayResolver(relayQueryClient);
        String json = "{\"kind\":10002,\"pubkey\":\"" + TARGET + "\",\"tags\":["
                + "[\"r\",\"wss://inbox.example\",\"read\"],"
                + "[\"r\",\"wss://outbox.example\",\"write\"],"
                + "[\"r\",\"wss://both.example\"],"
                + "[\"p\",\"ignored\"],"
                + "[\"r\",\"http://not-a-relay\"]"
                + "]}";
        when(relayQueryClient.query(any(), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of((JsonNode) MAPPER.readTree(json)), true));

        // Act
        Set<String> relays = resolver.resolve(TARGET, List.of("wss://default"), 10);

        // Assert
        assertThat(relays).containsExactlyInAnyOrder(
                "wss://inbox.example", "wss://outbox.example", "wss://both.example");
        assertThat(relays).noneMatch(url -> url.startsWith("http"));
    }

    /** A profile with no relay-list event resolves to an empty set. */
    @Test
    void shouldReturnEmptyWhenNoRelayList() throws Exception {
        // Arrange
        Nip65RelayResolver resolver = new Nip65RelayResolver(relayQueryClient);
        when(relayQueryClient.query(any(), any(), anyLong()))
                .thenReturn(new RelayQueryResult(List.of(), true));

        // Act
        Set<String> relays = resolver.resolve(TARGET, List.of("wss://default"), 10);

        // Assert
        assertThat(relays).isEmpty();
    }
}
