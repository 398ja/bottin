package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity summarising one scheduled reach-calculation run, for operational
 * observability (how many profiles were processed, skipped, and failed).
 */
@Entity
@Table(name = "reach_calculation_runs",
        indexes = {
                @Index(name = "idx_reach_runs_started_at", columnList = "started_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ReachCalculationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "profiles_total", nullable = false)
    private int profilesTotal;

    @Column(name = "profiles_processed", nullable = false)
    private int profilesProcessed;

    @Column(name = "profiles_skipped", nullable = false)
    private int profilesSkipped;

    @Column(name = "gather_failures", nullable = false)
    private int gatherFailures;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
