package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity for the deployment's single settings row.
 *
 * <p>Unlike every other entity here the identifier is not generated: the row is
 * seeded by the {@code V4__settings} migration, and the
 * {@code settings_singleton} constraint makes a second row unrepresentable
 * rather than merely unlikely.
 *
 * <p>The relay lists are carried as raw JSON exactly as
 * {@link Nip05RecordEntity} carries {@code relaysJson}. Serialising them belongs
 * to the service that owns the {@code ObjectMapper}, so that Jackson stays out
 * of the persistence layer.
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SettingsEntity {

    /**
     * The identifier of the one row this table is allowed to hold.
     */
    public static final long SINGLETON_ID = 1L;

    private static final String EMPTY_RELAYS_JSON = "[]";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private Long id = SINGLETON_ID;

    @Column(name = "blossom_url", length = 512)
    private String blossomUrl;

    @Column(name = "default_relays_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String defaultRelaysJson = EMPTY_RELAYS_JSON;

    @Column(name = "discovery_relays_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String discoveryRelaysJson = EMPTY_RELAYS_JSON;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onWrite() {
        if (defaultRelaysJson == null) {
            defaultRelaysJson = EMPTY_RELAYS_JSON;
        }
        if (discoveryRelaysJson == null) {
            discoveryRelaysJson = EMPTY_RELAYS_JSON;
        }
        updatedAt = Instant.now();
    }
}
