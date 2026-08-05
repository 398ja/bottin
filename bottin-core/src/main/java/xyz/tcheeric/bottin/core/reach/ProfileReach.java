package xyz.tcheeric.bottin.core.reach;

import java.time.Instant;

/**
 * Immutable value describing the most recently calculated reach for a profile.
 *
 * <p>Reach is the number of distinct users whose current NIP-02 contact list
 * includes the target profile (its followers).
 *
 * @param pubkey       the profile's canonical 64-character lowercase hex public key
 * @param npub         the NIP-19 bech32 encoding of the same key
 * @param reachCount   the number of distinct current followers (never negative)
 * @param complete     {@code true} if gathered from a full set of relays;
 *                     {@code false} if some relays did not respond and the figure may undercount
 * @param calculatedAt when this figure was calculated
 */
public record ProfileReach(String pubkey, String npub, long reachCount, boolean complete, Instant calculatedAt) {
}
