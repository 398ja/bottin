package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;

import java.util.List;
import java.util.Optional;

/**
 * Repository for administrators permitted to sign in to the dashboard.
 *
 * <p>Every query is by public key, because that is the only question asked of
 * this table: sign-in asks whether a proven key is an administrator, and the
 * settings page asks for the whole list. The unique index on {@code pubkey}
 * serves the first.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {

    /** The sign-in question, asked on every ACL resolution. */
    Optional<AdminUserEntity> findByPubkey(String pubkey);

    boolean existsByPubkey(String pubkey);

    /** Oldest first, so the list does not reorder itself between visits. */
    List<AdminUserEntity> findAllByOrderByCreatedAtAsc();

    void deleteByPubkey(String pubkey);
}
