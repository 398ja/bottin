package xyz.tcheeric.bottin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;

import java.time.Instant;

/**
 * JPA entity representing a NIP-05 record in Bottin.
 */
@Entity
@Table(name = "nip05_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_nip05_domain_username",
                        columnNames = {"domain_id", "username"})
        },
        indexes = {
                @Index(name = "idx_nip05_records_pubkey", columnList = "pubkey"),
                @Index(name = "idx_nip05_records_domain_username", columnList = "domain_id, username")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Nip05RecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private DomainEntity domain;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "pubkey", nullable = false, length = 64)
    private String pubkey;

    @Column(name = "relays_json", columnDefinition = "TEXT")
    @Builder.Default
    private String relaysJson = "[]";

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

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
        if (username != null) {
            username = username.toLowerCase();
        }
        if (relaysJson == null) {
            relaysJson = "[]";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the full NIP-05 identifier.
     */
    public String getNip05() {
        return username + "@" + domain.getName();
    }

    /**
     * Converts this entity to a Nip05RecordData value object.
     */
    public Nip05RecordData toNip05RecordData() {
        return Nip05RecordData.builder()
                .id(id)
                .domainId(domain.getId())
                .username(username)
                .domain(domain.getName())
                .pubkey(pubkey)
                .relaysJson(relaysJson)
                .enabled(enabled)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Creates an entity from a Nip05RecordData value object.
     * Note: The DomainEntity must be set separately.
     */
    public static Nip05RecordEntity fromNip05RecordData(Nip05RecordData data, DomainEntity domainEntity) {
        return Nip05RecordEntity.builder()
                .id(data.getId())
                .domain(domainEntity)
                .username(data.getUsername())
                .pubkey(data.getPubkey())
                .relaysJson(data.getRelaysJson())
                .enabled(data.isEnabled())
                .createdAt(data.getCreatedAt())
                .updatedAt(data.getUpdatedAt())
                .build();
    }
}
