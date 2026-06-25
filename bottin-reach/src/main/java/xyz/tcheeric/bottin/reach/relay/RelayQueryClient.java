package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import nostr.client.springwebsocket.NostrRelayClient;
import nostr.event.filter.EventFilter;
import nostr.event.message.ReqMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper over nostr-java's {@link NostrRelayClient} that runs a single
 * REQ subscription against one relay and collects the resulting events until the
 * relay signals EOSE (a complete read) or the timeout elapses (a partial read).
 */
@Component
@Slf4j
public class RelayQueryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Subscribes to {@code relayUrl} with {@code filter} and collects events until
     * EOSE or {@code timeoutSeconds}.
     *
     * @throws Exception if the relay cannot be connected to or the subscription fails
     */
    public RelayQueryResult query(String relayUrl, EventFilter filter, long timeoutSeconds) throws Exception {
        List<JsonNode> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch eose = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);
        ReqMessage request = new ReqMessage(subscriptionId(), filter);

        try (NostrRelayClient client = new NostrRelayClient(relayUrl)) {
            AutoCloseable subscription = client.subscribe(
                    request,
                    raw -> collect(raw, events),
                    error -> {
                        failed.set(true);
                        log.debug("relay_query_error relay={} error={}", relayUrl, error.toString());
                        eose.countDown();
                    },
                    eose::countDown);
            try {
                boolean completed = eose.await(timeoutSeconds, TimeUnit.SECONDS);
                return new RelayQueryResult(new ArrayList<>(events), completed && !failed.get());
            } finally {
                subscription.close();
            }
        }
    }

    private void collect(String raw, List<JsonNode> events) {
        try {
            JsonNode node = MAPPER.readTree(raw);
            JsonNode event = node;
            if (node.isArray() && node.size() >= 3 && "EVENT".equals(node.get(0).asText())) {
                event = node.get(2);
            }
            if (event != null && event.hasNonNull("pubkey")) {
                events.add(event);
            }
        } catch (Exception e) {
            log.debug("relay_event_parse_failed error={}", e.toString());
        }
    }

    private String subscriptionId() {
        return "bottin-reach-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
