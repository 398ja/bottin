package xyz.tcheeric.bottin.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.persistence.entity.SettingsEntity;
import xyz.tcheeric.bottin.persistence.repository.SettingsRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-05: Settings Repository Integration Tests.
 *
 * <p>Verifies against PostgreSQL the two guarantees the settings table rests on
 * and that no unit test can reach: that the V4 migration seeds the row, so
 * "no settings row" is never a state the application handles, and that the
 * singleton constraint makes a second row unrepresentable rather than unlikely.
 */
@Transactional
@Disabled("""
        Blocked on bottin-it, not on the settings feature. Two things must change first.

        1. The module's Spring context does not start. BottinAutoConfiguration
           component-scans xyz.tcheeric.bottin.api from an @AutoConfiguration class,
           which registers SecurityConfig too late for @ConditionalOnDefaultWebSecurity
           to back off, so Spring Boot's default filter chain collides with it. Every
           IT in this module fails this way, and did so before this feature existed.

        2. These tests assert what the V4 migration produces, but application-test.yml
           sets spring.flyway.enabled=false with ddl-auto=create-drop, so migrations
           never run here — Hibernate builds the schema instead, without the seeded row
           or the settings_singleton constraint. This class needs Flyway enabled and
           ddl-auto=none to mean anything.

        See T010a in specs/004-admin-settings/tasks.md.""")
class SettingsRepositoryIT extends BaseIntegrationTest {

    private static final String EMPTY_RELAYS_JSON = "[]";
    private static final int SEEDED_RATE_LIMIT = 30;

    @Autowired
    private SettingsRepository settingsRepository;

    /**
     * Tests that the migration leaves exactly one settings row, unconfigured but
     * present, so first boot has values rather than an absence to handle.
     */
    @Test
    void shouldSeedTheSettingsRowWhenTheMigrationRuns() {
        // Act: Read the singleton row a freshly migrated database should hold
        SettingsEntity settings = settingsRepository.findById(SettingsEntity.SINGLETON_ID).orElseThrow();

        // Assert: Unconfigured is represented by values, not by absence
        assertThat(settings.getBlossomUrl()).isNull();
        assertThat(settings.getDefaultRelaysJson()).isEqualTo(EMPTY_RELAYS_JSON);
        assertThat(settings.getDiscoveryRelaysJson()).isEqualTo(EMPTY_RELAYS_JSON);
        assertThat(settings.getRateLimitPerMinute()).isEqualTo(SEEDED_RATE_LIMIT);
        assertThat(settings.getUpdatedAt()).isNotNull();
    }

    /**
     * Tests that the settings table holds one row and only one, since a second
     * would make "the deployment's settings" ambiguous.
     */
    @Test
    void shouldRejectASecondSettingsRow() {
        // Arrange: A settings row carrying an identifier other than the singleton
        SettingsEntity second = SettingsEntity.builder()
                .id(SettingsEntity.SINGLETON_ID + 1)
                .rateLimitPerMinute(SEEDED_RATE_LIMIT)
                .updatedAt(Instant.now())
                .build();

        // Act & Assert: The singleton constraint refuses it
        assertThatThrownBy(() -> settingsRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Tests that saving stamps updated_at, which is what makes "did anyone
     * change this?" answerable without opening the database.
     */
    @Test
    void shouldAdvanceUpdatedAtWhenSettingsAreSaved() {
        // Arrange: Save one change and record the resulting timestamp
        SettingsEntity settings = settingsRepository.findById(SettingsEntity.SINGLETON_ID).orElseThrow();
        settings.setBlossomUrl("https://blossom.example");
        Instant firstSave = settingsRepository.saveAndFlush(settings).getUpdatedAt();

        // Act: Save a second change
        settings.setRateLimitPerMinute(SEEDED_RATE_LIMIT + 1);
        Instant secondSave = settingsRepository.saveAndFlush(settings).getUpdatedAt();

        // Assert: The later save carries the later timestamp
        assertThat(secondSave).isAfter(firstSave);
    }
}
