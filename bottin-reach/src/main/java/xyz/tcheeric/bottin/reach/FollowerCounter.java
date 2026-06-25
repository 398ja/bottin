package xyz.tcheeric.bottin.reach;

import xyz.tcheeric.bottin.reach.relay.ContactListEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure distinct-follower counting from gathered NIP-02 (kind-3) events.
 *
 * <p>Algorithm (per the feature research): de-duplicate by event id, keep the
 * newest contact list per author (highest {@code created_at}, tie-broken by the
 * lexicographically lower event id per NIP-01 replaceable-event rules), then
 * count authors whose newest list still tags the target (excludes unfollows and
 * self-follow).
 */
public final class FollowerCounter {

    private FollowerCounter() {
    }

    /**
     * Counts the distinct current followers of {@code targetHex}.
     */
    public static long countFollowers(Collection<ContactListEvent> events, String targetHex) {
        Map<String, ContactListEvent> latestByAuthor = new HashMap<>();
        for (ContactListEvent event : events) {
            if (event == null || event.authorPubkey() == null) {
                continue;
            }
            ContactListEvent current = latestByAuthor.get(event.authorPubkey());
            if (current == null || isNewer(event, current)) {
                latestByAuthor.put(event.authorPubkey(), event);
            }
        }

        long followers = 0;
        for (ContactListEvent latest : latestByAuthor.values()) {
            if (latest.authorPubkey().equals(targetHex)) {
                continue; // a profile following itself is not a follower
            }
            if (latest.taggedPubkeys() != null && latest.taggedPubkeys().contains(targetHex)) {
                followers++;
            }
        }
        return followers;
    }

    private static boolean isNewer(ContactListEvent candidate, ContactListEvent current) {
        if (candidate.createdAt() != current.createdAt()) {
            return candidate.createdAt() > current.createdAt();
        }
        // NIP-01: with equal timestamps, the event with the lowest id is retained.
        String candidateId = candidate.eventId() == null ? "" : candidate.eventId();
        String currentId = current.eventId() == null ? "" : current.eventId();
        return candidateId.compareTo(currentId) < 0;
    }
}
