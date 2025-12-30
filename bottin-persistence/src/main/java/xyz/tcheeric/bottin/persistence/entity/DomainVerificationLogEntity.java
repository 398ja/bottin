package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * JPA entity representing a domain verification attempt log entry.
 */
@Entity
@Table(name = "domain_verification_logs", indexes = {
        @Index(name = "idx_dv_logs_domain_id", columnList = "domain_id"),
        @Index(name = "idx_dv_logs_attempted_at", columnList = "attempted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DomainVerificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private DomainEntity domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private VerificationMethod method;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }
}
