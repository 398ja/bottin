package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import xyz.tcheeric.bottin.core.model.VerificationLogData;
import xyz.tcheeric.bottin.core.model.VerificationLogData.VerificationResultType;
import xyz.tcheeric.bottin.core.model.VerificationLogData.VerificationType;

import java.time.Instant;

/**
 * JPA entity representing a verification log entry for audit trail.
 */
@Entity
@Table(name = "verification_logs", indexes = {
        @Index(name = "idx_verification_logs_nip05", columnList = "nip05"),
        @Index(name = "idx_verification_logs_verified_at", columnList = "verified_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class VerificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nip05", nullable = false, length = 511)
    private String nip05;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 20)
    private VerificationType verificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private VerificationResultType result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "remote_response", columnDefinition = "TEXT")
    private String remoteResponse;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @PrePersist
    protected void onCreate() {
        if (verifiedAt == null) {
            verifiedAt = Instant.now();
        }
    }

    /**
     * Converts this entity to a VerificationLogData value object.
     */
    public VerificationLogData toVerificationLogData() {
        return VerificationLogData.builder()
                .id(id)
                .nip05(nip05)
                .verificationType(verificationType)
                .result(result)
                .errorMessage(errorMessage)
                .remoteResponse(remoteResponse)
                .verifiedAt(verifiedAt)
                .build();
    }

    /**
     * Creates an entity from a VerificationLogData value object.
     */
    public static VerificationLogEntity fromVerificationLogData(VerificationLogData data) {
        return VerificationLogEntity.builder()
                .id(data.getId())
                .nip05(data.getNip05())
                .verificationType(data.getVerificationType())
                .result(data.getResult())
                .errorMessage(data.getErrorMessage())
                .remoteResponse(data.getRemoteResponse())
                .verifiedAt(data.getVerifiedAt())
                .build();
    }
}
