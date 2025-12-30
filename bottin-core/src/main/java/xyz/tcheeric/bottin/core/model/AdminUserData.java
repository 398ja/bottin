package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable value object representing an admin dashboard user.
 */
@Value
@Builder(toBuilder = true)
public class AdminUserData {

    /**
     * Unique identifier for the user.
     */
    Long id;

    /**
     * Username for login.
     */
    String username;

    /**
     * BCrypt hashed password.
     */
    String passwordHash;

    /**
     * Optional Nostr public key for the admin.
     */
    String pubkey;

    /**
     * User's role.
     */
    AdminRole role;

    /**
     * Whether the user account is enabled.
     */
    boolean enabled;

    /**
     * When the user was created.
     */
    Instant createdAt;

    /**
     * Creates a new admin user.
     */
    public static AdminUserData createNew(String username, String passwordHash, AdminRole role) {
        return AdminUserData.builder()
                .username(username)
                .passwordHash(passwordHash)
                .role(role)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Returns this user with enabled status changed.
     */
    public AdminUserData withEnabled(boolean enabled) {
        return this.toBuilder()
                .enabled(enabled)
                .build();
    }

    /**
     * Returns this user with updated password.
     */
    public AdminUserData withPasswordHash(String passwordHash) {
        return this.toBuilder()
                .passwordHash(passwordHash)
                .build();
    }

    /**
     * Returns this user with updated role.
     */
    public AdminUserData withRole(AdminRole role) {
        return this.toBuilder()
                .role(role)
                .build();
    }

    /**
     * Checks if this user has admin privileges.
     */
    public boolean isAdmin() {
        return role == AdminRole.ADMIN;
    }
}
