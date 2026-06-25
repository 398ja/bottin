package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nostr.event.filter.EventFilter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a profile's NIP-65 (kind-10002) advertised relay list by querying the
 * default application relays for the profile's relay-list metadata event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Nip65RelayResolver {

    private static final int KIND_RELAY_LIST = 10002;

    private final RelayQueryClient relayQueryClient;

    /**
     * Returns the {@code ws(s)://} relay URLs advertised by {@code targetHex} via
     * NIP-65, discovered by querying {@code defaultRelays}. Returns an empty set if
     * the profile has no relay list or none could be fetched.
     */
    public Set<String> resolve(String targetHex, List<String> defaultRelays, long timeoutSeconds) {
        Set<String> relays = new LinkedHashSet<>();
        EventFilter filter = EventFilter.builder()
                .kind(KIND_RELAY_LIST)
                .author(targetHex)
                .limit(5)
                .build();

        for (String relay : defaultRelays) {
            try {
                RelayQueryResult result = relayQueryClient.query(relay, filter, timeoutSeconds);
                for (JsonNode event : result.events()) {
                    if (event.path("kind").asInt() != KIND_RELAY_LIST) {
                        continue;
                    }
                    for (JsonNode tag : event.path("tags")) {
                        if (tag.isArray() && tag.size() >= 2 && "r".equals(tag.get(0).asText())) {
                            String url = tag.get(1).asText();
                            if (url != null && (url.startsWith("wss://") || url.startsWith("ws://"))) {
                                relays.add(url);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("nip65_resolve_failed relay={} target={} error={}", relay, targetHex, e.toString());
            }
        }
        return relays;
    }
}
