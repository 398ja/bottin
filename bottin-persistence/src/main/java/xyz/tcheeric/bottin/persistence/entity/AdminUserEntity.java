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
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.core.model.AdminUserData;

import java.time.Instant;

/**
 * JPA entity for an administrator permitted to sign in to the dashboard.
 *
 * <p>Holds ordinary administrators only. The configured master key is
 * deployment configuration and never a row here, so no row — and no sequence of
 * edits to this table — can demote or lock out the super administrator.
 */
@Entity
@Table(name = "admin_users", indexes = {
        @Index(name = "idx_admin_users_pubkey", columnList = "pubkey", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AdminUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canonical lowercase hex (NIP-01). Unique, so one key is one administrator. */
    @Column(name = "pubkey", nullable = false, unique = true, length = 64)
    private String pubkey;

    @Column(name = "label", length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private AdminRole role = AdminRole.ADMIN;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "added_by_pubkey", length = 64)
    private String addedByPubkey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public AdminUserData toAdminUserData() {
        return AdminUserData.builder()
                .id(id)
                .pubkey(pubkey)
                .label(label)
                .role(role)
                .enabled(enabled)
                .addedByPubkey(addedByPubkey)
                .createdAt(createdAt)
                .build();
    }

    public static AdminUserEntity fromAdminUserData(AdminUserData data) {
        return AdminUserEntity.builder()
                .id(data.getId())
                .pubkey(data.getPubkey())
                .label(data.getLabel())
                .role(data.getRole())
                .enabled(data.isEnabled())
                .addedByPubkey(data.getAddedByPubkey())
                .createdAt(data.getCreatedAt())
                .build();
    }
}
