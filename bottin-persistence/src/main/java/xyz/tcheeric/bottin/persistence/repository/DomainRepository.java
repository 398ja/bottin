package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for DomainEntity.
 */
@Repository
public interface DomainRepository extends JpaRepository<DomainEntity, Long> {

    /**
     * Finds a domain by its name.
     */
    Optional<DomainEntity> findByName(String name);

    /**
     * Checks if a domain with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Finds all verified domains.
     */
    List<DomainEntity> findByVerifiedTrue();

    /**
     * Finds all unverified domains.
     */
    List<DomainEntity> findByVerifiedFalse();

    /**
     * Finds domains owned by a specific pubkey.
     */
    List<DomainEntity> findByOwnerPubkey(String ownerPubkey);

    /**
     * Counts verified domains.
     */
    long countByVerifiedTrue();

    /**
     * Counts unverified domains.
     */
    long countByVerifiedFalse();

    /**
     * Finds domains with pending verification tokens.
     */
    @Query("SELECT d FROM DomainEntity d WHERE d.verified = false AND d.verificationToken IS NOT NULL")
    List<DomainEntity> findPendingVerification();
}
