package xyz.tcheeric.bottin.persistence.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import xyz.tcheeric.bottin.persistence.entity.ProfileReachEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository integration tests for {@link ProfileReachRepository}, exercising the
 * profile_reach mapping against a real JPA provider.
 */
@DataJpaTest
@ActiveProfiles("test")
class ProfileReachRepositoryTest {

    private static final String PUBKEY = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2";

    @Autowired
    private ProfileReachRepository repository;

    /** A reach figure can be saved and retrieved by pubkey, with timestamps populated. */
    @Test
    void shouldSaveAndFindByPubkey() {
        // Arrange
        ProfileReachEntity entity = ProfileReachEntity.builder()
                .pubkey(PUBKEY)
                .reachCount(1542L)
                .complete(true)
                .calculatedAt(Instant.parse("2026-06-25T06:00:12Z"))
                .build();

        // Act
        repository.save(entity);
        Optional<ProfileReachEntity> found = repository.findByPubkey(PUBKEY);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getReachCount()).isEqualTo(1542L);
        assertThat(found.get().isComplete()).isTrue();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    /** Re-saving the same profile updates the figure in place (the upsert path) without a new row. */
    @Test
    void shouldUpdateExistingFigureInPlace() {
        // Arrange: an initial complete figure
        ProfileReachEntity initial = repository.save(ProfileReachEntity.builder()
                .pubkey(PUBKEY).reachCount(100L).complete(true).calculatedAt(Instant.now()).build());
        Long id = initial.getId();

        // Act: a later run finds and updates the same row
        ProfileReachEntity existing = repository.findByPubkey(PUBKEY).orElseThrow();
        existing.setReachCount(250L);
        existing.setComplete(false);
        repository.save(existing);

        // Assert: same id, updated values
        Optional<ProfileReachEntity> found = repository.findByPubkey(PUBKEY);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
        assertThat(found.get().getReachCount()).isEqualTo(250L);
        assertThat(found.get().isComplete()).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    /** A genuine reach of zero is stored as a row (distinct from "no figure available"). */
    @Test
    void shouldStoreZeroReach() {
        // Arrange / Act
        repository.save(ProfileReachEntity.builder()
                .pubkey(PUBKEY).reachCount(0L).complete(true).calculatedAt(Instant.now()).build());

        // Assert
        assertThat(repository.findByPubkey(PUBKEY)).isPresent()
                .get().extracting(ProfileReachEntity::getReachCount).isEqualTo(0L);
    }

    /** The pubkey is unique: a second row for the same pubkey is rejected. */
    @Test
    void shouldRejectDuplicatePubkey() {
        // Arrange: an existing figure
        repository.saveAndFlush(ProfileReachEntity.builder()
                .pubkey(PUBKEY).reachCount(10L).complete(true).calculatedAt(Instant.now()).build());

        // Act / Assert: a second distinct row with the same pubkey violates the unique constraint
        ProfileReachEntity duplicate = ProfileReachEntity.builder()
                .pubkey(PUBKEY).reachCount(20L).complete(true).calculatedAt(Instant.now()).build();
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
