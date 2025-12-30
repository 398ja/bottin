package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.core.model.VerificationLogData.VerificationResultType;
import xyz.tcheeric.bottin.core.model.VerificationLogData.VerificationType;
import xyz.tcheeric.bottin.persistence.entity.VerificationLogEntity;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for VerificationLogEntity.
 */
@Repository
public interface VerificationLogRepository extends JpaRepository<VerificationLogEntity, Long> {

    /**
     * Finds all logs for a specific NIP-05 identifier.
     */
    List<VerificationLogEntity> findByNip05(String nip05);

    /**
     * Finds all logs for a NIP-05 identifier (paginated).
     */
    Page<VerificationLogEntity> findByNip05(String nip05, Pageable pageable);

    /**
     * Finds logs by verification type.
     */
    Page<VerificationLogEntity> findByVerificationType(VerificationType type, Pageable pageable);

    /**
     * Finds logs by result.
     */
    Page<VerificationLogEntity> findByResult(VerificationResultType result, Pageable pageable);

    /**
     * Finds logs after a given timestamp.
     */
    Page<VerificationLogEntity> findByVerifiedAtAfter(Instant after, Pageable pageable);

    /**
     * Finds logs between two timestamps.
     */
    Page<VerificationLogEntity> findByVerifiedAtBetween(Instant start, Instant end, Pageable pageable);

    /**
     * Counts logs by result type.
     */
    long countByResult(VerificationResultType result);

    /**
     * Counts logs by verification type.
     */
    long countByVerificationType(VerificationType type);

    /**
     * Finds the most recent logs.
     */
    @Query("SELECT l FROM VerificationLogEntity l ORDER BY l.verifiedAt DESC")
    List<VerificationLogEntity> findRecent(Pageable pageable);

    /**
     * Finds failed verifications in the last N hours.
     */
    @Query("SELECT l FROM VerificationLogEntity l " +
           "WHERE l.result = 'FAILURE' AND l.verifiedAt > :since " +
           "ORDER BY l.verifiedAt DESC")
    List<VerificationLogEntity> findRecentFailures(@Param("since") Instant since);

    /**
     * Counts successful verifications for a NIP-05.
     */
    @Query("SELECT COUNT(l) FROM VerificationLogEntity l " +
           "WHERE l.nip05 = :nip05 AND l.result = 'SUCCESS'")
    long countSuccessfulByNip05(@Param("nip05") String nip05);

    /**
     * Deletes logs older than a given timestamp (for cleanup).
     */
    void deleteByVerifiedAtBefore(Instant before);
}
