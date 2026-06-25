package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The outcome of a single relay subscription: the events received, and whether
 * the relay signalled end-of-stored-events (EOSE) within the timeout.
 *
 * @param events      the event JSON nodes received
 * @param reachedEose {@code true} if the relay reached EOSE (a complete read)
 */
public record RelayQueryResult(List<JsonNode> events, boolean reachedEose) {
}
