package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity holding the latest calculated reach for one tracked profile.
 *
 * <p>One row per pubkey; upserted on each successful scheduled calculation.
 */
@Entity
@Table(name = "profile_reach",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_profile_reach_pubkey", columnNames = {"pubkey"})
        },
        indexes = {
                @Index(name = "idx_profile_reach_pubkey", columnList = "pubkey")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ProfileReachEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pubkey", nullable = false, unique = true, length = 64)
    private String pubkey;

    @Column(name = "reach_count", nullable = false)
    private long reachCount;

    /**
     * {@code true} if every targeted relay responded when this figure was gathered;
     * {@code false} if it was built from a partial set of relays and may undercount.
     */
    @Column(name = "complete", nullable = false)
    @Builder.Default
    private boolean complete = true;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (calculatedAt == null) {
            calculatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
