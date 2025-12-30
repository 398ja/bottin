package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for AdminUserEntity.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {

    /**
     * Finds an admin user by username.
     */
    Optional<AdminUserEntity> findByUsername(String username);

    /**
     * Checks if an admin user with the given username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Finds all enabled admin users.
     */
    List<AdminUserEntity> findByEnabledTrue();

    /**
     * Finds admin users by role.
     */
    List<AdminUserEntity> findByRole(AdminRole role);

    /**
     * Finds an admin user by Nostr pubkey.
     */
    Optional<AdminUserEntity> findByPubkey(String pubkey);

    /**
     * Counts enabled admin users.
     */
    long countByEnabledTrue();

    /**
     * Counts admin users by role.
     */
    long countByRole(AdminRole role);
}
