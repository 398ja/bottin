package xyz.tcheeric.bottin.reach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.core.reach.ProfileReach;
import xyz.tcheeric.bottin.persistence.entity.ProfileReachEntity;
import xyz.tcheeric.bottin.persistence.repository.ProfileReachRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read path that serves stored reach figures.
 */
@ExtendWith(MockitoExtension.class)
class ReachQueryServiceImplTest {

    private static final String HEX = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2";

    @Mock
    private ProfileReachRepository profileReachRepository;

    // PubkeyCodec is a pure utility, used directly rather than mocked.
    private final PubkeyCodec pubkeyCodec = new PubkeyCodec();

    /** A stored figure is returned with its fields mapped and an npub derived. */
    @Test
    void shouldReturnStoredReach() {
        // Given: a stored figure for the hex pubkey
        ReachQueryServiceImpl wired = new ReachQueryServiceImpl(profileReachRepository, pubkeyCodec);
        ProfileReachEntity entity = ProfileReachEntity.builder()
                .pubkey(HEX)
                .reachCount(1542L)
                .complete(true)
                .calculatedAt(Instant.parse("2026-06-25T06:00:12Z"))
                .build();
        when(profileReachRepository.findByPubkey(HEX)).thenReturn(Optional.of(entity));

        // When
        Optional<ProfileReach> result = wired.findReach(HEX);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().reachCount()).isEqualTo(1542L);
        assertThat(result.get().complete()).isTrue();
        assertThat(result.get().npub()).startsWith("npub1");
    }

    /** A profile with no stored figure yields an empty result (the controller maps this to 404). */
    @Test
    void shouldReturnEmptyWhenNotCalculated() {
        // Given: no stored figure
        ReachQueryServiceImpl wired = new ReachQueryServiceImpl(profileReachRepository, pubkeyCodec);
        when(profileReachRepository.findByPubkey(HEX)).thenReturn(Optional.empty());

        // When
        Optional<ProfileReach> result = wired.findReach(HEX);

        // Then
        assertThat(result).isEmpty();
    }
}
