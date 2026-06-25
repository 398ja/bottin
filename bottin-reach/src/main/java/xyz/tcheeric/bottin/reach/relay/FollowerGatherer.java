package xyz.tcheeric.bottin.reach.relay;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nostr.event.filter.EventFilter;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.reach.FollowerCounter;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gathers NIP-02 (kind-3) follower events for a target profile across a set of
 * relays and reduces them to a distinct follower count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FollowerGatherer {

    private static final int KIND_CONTACT_LIST = 3;

    private final RelayQueryClient relayQueryClient;

    /**
     * Queries each relay for kind-3 events tagging {@code targetHex}, merges and
     * de-duplicates them globally, and returns the distinct follower count along
     * with whether the gather was complete and whether any relay responded.
     */
    public GatherResult gather(String targetHex, Collection<String> relays, int perRelayLimit, long timeoutSeconds) {
        Map<String, ContactListEvent> byEventId = new HashMap<>();
        int relaysQueried = 0;
        int relaysResponded = 0;
        int relaysComplete = 0;

        for (String relay : relays) {
            relaysQueried++;
            EventFilter filter = EventFilter.builder()
                    .kind(KIND_CONTACT_LIST)
                    .addTagFilter("p", targetHex)
                    .limit(perRelayLimit)
                    .build();
            try {
                RelayQueryResult result = relayQueryClient.query(relay, filter, timeoutSeconds);
                relaysResponded++;
                if (result.reachedEose()) {
                    relaysComplete++;
                }
                for (JsonNode event : result.events()) {
                    ContactListEvent contactList = toContactListEvent(event);
                    if (contactList != null) {
                        byEventId.putIfAbsent(dedupeKey(contactList), contactList);
                    }
                }
            } catch (Exception e) {
                log.debug("follower_gather_relay_failed relay={} target={} error={}", relay, targetHex, e.toString());
            }
        }

        long reach = FollowerCounter.countFollowers(byEventId.values(), targetHex);
        boolean complete = relaysQueried > 0 && relaysComplete == relaysQueried;
        boolean anyData = relaysResponded > 0;
        return new GatherResult(reach, complete, anyData);
    }

    private ContactListEvent toContactListEvent(JsonNode event) {
        if (event == null || !event.hasNonNull("pubkey")) {
            return null;
        }
        if (event.path("kind").asInt() != KIND_CONTACT_LIST) {
            return null;
        }
        String id = event.path("id").asText(null);
        String author = event.get("pubkey").asText();
        long createdAt = event.path("created_at").asLong();
        Set<String> tagged = new HashSet<>();
        for (JsonNode tag : event.path("tags")) {
            if (tag.isArray() && tag.size() >= 2 && "p".equals(tag.get(0).asText())) {
                tagged.add(tag.get(1).asText());
            }
        }
        return new ContactListEvent(id, author, createdAt, tagged);
    }

    private String dedupeKey(ContactListEvent event) {
        return event.eventId() != null ? event.eventId() : event.authorPubkey() + ":" + event.createdAt();
    }
}
