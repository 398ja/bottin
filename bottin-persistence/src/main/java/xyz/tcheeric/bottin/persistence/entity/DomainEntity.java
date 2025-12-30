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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * JPA entity representing a registered domain in Bottin.
 */
@Entity
@Table(name = "domains", indexes = {
        @Index(name = "idx_domains_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DomainEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 20)
    private VerificationMethod verificationMethod;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "owner_pubkey", length = 64)
    private String ownerPubkey;

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
        if (name != null) {
            name = name.toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Converts this entity to a DomainData value object.
     */
    public DomainData toDomainData() {
        return DomainData.builder()
                .id(id)
                .name(name)
                .verified(verified)
                .verificationToken(verificationToken)
                .verificationMethod(verificationMethod)
                .verifiedAt(verifiedAt)
                .ownerPubkey(ownerPubkey)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Creates an entity from a DomainData value object.
     */
    public static DomainEntity fromDomainData(DomainData data) {
        return DomainEntity.builder()
                .id(data.getId())
                .name(data.getName())
                .verified(data.isVerified())
                .verificationToken(data.getVerificationToken())
                .verificationMethod(data.getVerificationMethod())
                .verifiedAt(data.getVerifiedAt())
                .ownerPubkey(data.getOwnerPubkey())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .build();
    }
}
