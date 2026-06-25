package xyz.tcheeric.bottin.reach.relay;

import java.util.Set;

/**
 * A gathered NIP-02 (kind-3) contact-list event, reduced to the fields needed
 * for distinct-follower counting.
 *
 * @param eventId        the event id (used for cross-relay de-duplication)
 * @param authorPubkey   the follower's hex public key (the event author)
 * @param createdAt      the event timestamp (seconds), used to keep the newest per author
 * @param taggedPubkeys  the hex public keys in the event's {@code p} tags (who the author follows)
 */
public record ContactListEvent(String eventId, String authorPubkey, long createdAt, Set<String> taggedPubkeys) {
}
