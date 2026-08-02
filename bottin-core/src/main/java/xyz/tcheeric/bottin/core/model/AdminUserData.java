package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * An administrator permitted to sign in to the dashboard.
 *
 * <p>Identified by a public key, not a username and password. The deployment
 * holds nothing that could sign on this administrator's behalf, which is the
 * property that makes keeping a stored administrator list safe at all.
 *
 * <p>The configured master key is deliberately <em>not</em> represented here.
 * Its authority comes from deployment configuration, and a stored copy could
 * disagree with it with no correct way to resolve the difference.
 */
@Value
@Builder(toBuilder = true)
public class AdminUserData {

    Long id;

    /**
     * Canonical lowercase hex (NIP-01). The identity, and the only field that
     * decides anything.
     */
    String pubkey;

    /**
     * What a person calls this administrator. Descriptive only — never consulted
     * at sign-in, so a misleading label cannot grant anybody anything.
     */
    String label;

    AdminRole role;

    boolean enabled;

    /**
     * The administrator who added this one, in canonical hex, so a change of
     * access is attributable.
     */
    String addedByPubkey;

    Instant createdAt;

    /**
     * A new administrator, enabled from the outset.
     *
     * @param pubkey        canonical lowercase hex; callers normalise before calling
     * @param label         optional human-readable description
     * @param addedByPubkey the administrator performing the addition, canonical hex
     */
    public static AdminUserData createNew(String pubkey, String label, String addedByPubkey) {
        return AdminUserData.builder()
                .pubkey(pubkey)
                .label(label)
                .role(AdminRole.ADMIN)
                .enabled(true)
                .addedByPubkey(addedByPubkey)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * This administrator with their enabled state changed. Retained because the
     * column exists so suspension can be added without a migration.
     */
    public AdminUserData withEnabled(boolean enabled) {
        return this.toBuilder()
                .enabled(enabled)
                .build();
    }
}
