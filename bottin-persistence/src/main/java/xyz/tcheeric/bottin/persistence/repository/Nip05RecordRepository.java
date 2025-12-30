package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for Nip05RecordEntity.
 */
@Repository
public interface Nip05RecordRepository extends JpaRepository<Nip05RecordEntity, Long> {

    /**
     * Finds a record by username and domain.
     */
    Optional<Nip05RecordEntity> findByUsernameAndDomain(String username, DomainEntity domain);

    /**
     * Finds a record by username and domain name.
     * Uses JOIN FETCH to eagerly load the domain for async context access.
     */
    @Query("SELECT r FROM Nip05RecordEntity r JOIN FETCH r.domain WHERE r.username = :username AND r.domain.name = :domainName")
    Optional<Nip05RecordEntity> findByUsernameAndDomainName(
            @Param("username") String username,
            @Param("domainName") String domainName);

    /**
     * Checks if a record exists for the username and domain.
     */
    boolean existsByUsernameAndDomain(String username, DomainEntity domain);

    /**
     * Checks if an enabled record exists for the username and domain name.
     */
    @Query("SELECT COUNT(r) > 0 FROM Nip05RecordEntity r " +
           "WHERE r.username = :username AND r.domain.name = :domainName AND r.enabled = true")
    boolean existsByUsernameAndDomainNameAndEnabledTrue(
            @Param("username") String username,
            @Param("domainName") String domainName);

    /**
     * Finds all records for a domain.
     */
    List<Nip05RecordEntity> findByDomain(DomainEntity domain);

    /**
     * Finds all records for a domain name (paginated).
     */
    @Query("SELECT r FROM Nip05RecordEntity r WHERE r.domain.name = :domainName")
    Page<Nip05RecordEntity> findByDomainName(@Param("domainName") String domainName, Pageable pageable);

    /**
     * Finds all enabled records for a domain name.
     * Uses JOIN FETCH to eagerly load the domain for async context access.
     */
    @Query("SELECT r FROM Nip05RecordEntity r JOIN FETCH r.domain WHERE r.domain.name = :domainName AND r.enabled = true")
    List<Nip05RecordEntity> findByDomainNameAndEnabledTrue(@Param("domainName") String domainName);

    /**
     * Finds a record by pubkey.
     */
    Optional<Nip05RecordEntity> findByPubkey(String pubkey);

    /**
     * Finds all records by pubkey.
     */
    List<Nip05RecordEntity> findAllByPubkey(String pubkey);

    /**
     * Counts records by domain.
     */
    long countByDomain(DomainEntity domain);

    /**
     * Counts enabled records by domain name.
     */
    @Query("SELECT COUNT(r) FROM Nip05RecordEntity r WHERE r.domain.name = :domainName AND r.enabled = true")
    long countByDomainNameAndEnabledTrue(@Param("domainName") String domainName);

    /**
     * Counts all enabled records.
     */
    long countByEnabledTrue();

    /**
     * Finds the most recent records (for dashboard).
     */
    @Query("SELECT r FROM Nip05RecordEntity r ORDER BY r.createdAt DESC")
    List<Nip05RecordEntity> findRecent(Pageable pageable);

    /**
     * Searches records by username containing the given string.
     */
    Page<Nip05RecordEntity> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    /**
     * Searches records by pubkey starting with the given string.
     */
    Page<Nip05RecordEntity> findByPubkeyStartingWith(String pubkeyPrefix, Pageable pageable);
}
