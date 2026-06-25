package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.ProfileReachEntity;

import java.util.Optional;

/**
 * Spring Data repository for {@link ProfileReachEntity}.
 */
@Repository
public interface ProfileReachRepository extends JpaRepository<ProfileReachEntity, Long> {

    /**
     * Finds the stored reach for a profile by its canonical hex public key.
     */
    Optional<ProfileReachEntity> findByPubkey(String pubkey);
}
