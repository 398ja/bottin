package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;

import java.time.Instant;
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

    /**
     * Finds verified domains that need re-verification.
     * Returns domains where verifiedAt is before the cutoff time.
     */
    @Query("SELECT d FROM DomainEntity d WHERE d.verified = true AND d.verifiedAt < :cutoffTime ORDER BY d.verifiedAt ASC")
    List<DomainEntity> findDomainsToReverify(@Param("cutoffTime") Instant cutoffTime, Pageable pageable);

    /**
     * Finds domains with expired verification tokens.
     * Returns unverified domains where token exists and was set before expiration time.
     */
    @Query("SELECT d FROM DomainEntity d WHERE d.verified = false AND d.verificationToken IS NOT NULL AND d.updatedAt < :expirationTime")
    List<DomainEntity> findDomainsWithExpiredTokens(@Param("expirationTime") Instant expirationTime);
}
