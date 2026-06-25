package xyz.tcheeric.bottin.reach;

import org.junit.jupiter.api.Test;
import xyz.tcheeric.bottin.reach.relay.ContactListEvent;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the distinct-follower counting algorithm: de-duplication by
 * author, keeping the newest contact list, and excluding unfollows and self-follow.
 */
class FollowerCounterTest {

    private static final String TARGET = "aaaa000000000000000000000000000000000000000000000000000000000000";
    private static final String OTHER = "bbbb000000000000000000000000000000000000000000000000000000000000";

    /** With no gathered events, the reach is zero. */
    @Test
    void shouldReturnZeroWhenNoEvents() {
        // Given / When
        long reach = FollowerCounter.countFollowers(List.of(), TARGET);

        // Then
        assertThat(reach).isZero();
    }

    /** A user whose latest contact list tags the target counts as one follower. */
    @Test
    void shouldCountUserWhoTagsTarget() {
        // Given: one follower's kind-3 event tagging the target
        ContactListEvent event = new ContactListEvent("e1", OTHER, 100L, Set.of(TARGET));

        // When
        long reach = FollowerCounter.countFollowers(List.of(event), TARGET);

        // Then
        assertThat(reach).isEqualTo(1);
    }

    /** A contact list that does not tag the target contributes no follower. */
    @Test
    void shouldNotCountUserWhoDoesNotTagTarget() {
        // Given: a contact list tagging someone else
        ContactListEvent event = new ContactListEvent("e1", OTHER, 100L, Set.of("cccc"));

        // When
        long reach = FollowerCounter.countFollowers(List.of(event), TARGET);

        // Then
        assertThat(reach).isZero();
    }

    /** The same follower seen on multiple relays is counted exactly once (SC-004). */
    @Test
    void shouldCountDuplicateFollowerAcrossRelaysOnce() {
        // Given: the same author's contact list gathered from two relays
        ContactListEvent fromRelayA = new ContactListEvent("e1", OTHER, 100L, Set.of(TARGET));
        ContactListEvent fromRelayB = new ContactListEvent("e1", OTHER, 100L, Set.of(TARGET));

        // When
        long reach = FollowerCounter.countFollowers(List.of(fromRelayA, fromRelayB), TARGET);

        // Then
        assertThat(reach).isEqualTo(1);
    }

    /** A user who removed the target from their newest contact list is not counted. */
    @Test
    void shouldExcludeUnfollowInNewestContactList() {
        // Given: an older event tagging the target, and a newer one that does not
        ContactListEvent older = new ContactListEvent("e1", OTHER, 100L, Set.of(TARGET));
        ContactListEvent newer = new ContactListEvent("e2", OTHER, 200L, Set.of("cccc"));

        // When
        long reach = FollowerCounter.countFollowers(List.of(older, newer), TARGET);

        // Then: the newest list wins → not a follower
        assertThat(reach).isZero();
    }

    /** A profile that lists itself is not counted as its own follower. */
    @Test
    void shouldExcludeSelfFollow() {
        // Given: the target's own contact list tagging itself
        ContactListEvent selfFollow = new ContactListEvent("e1", TARGET, 100L, Set.of(TARGET));

        // When
        long reach = FollowerCounter.countFollowers(List.of(selfFollow), TARGET);

        // Then
        assertThat(reach).isZero();
    }

    /** Distinct followers are summed correctly. */
    @Test
    void shouldCountDistinctFollowers() {
        // Given: two different authors tagging the target
        ContactListEvent a = new ContactListEvent("e1", OTHER, 100L, Set.of(TARGET));
        ContactListEvent b = new ContactListEvent("e2",
                "cccc000000000000000000000000000000000000000000000000000000000000", 100L, Set.of(TARGET));

        // When
        long reach = FollowerCounter.countFollowers(List.of(a, b), TARGET);

        // Then
        assertThat(reach).isEqualTo(2);
    }
}
