package xyz.tcheeric.bottin.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.tcheeric.bottin.persistence.entity.DomainVerificationLogEntity;

import java.util.List;

/**
 * Spring Data repository for DomainVerificationLogEntity.
 */
@Repository
public interface DomainVerificationLogRepository extends JpaRepository<DomainVerificationLogEntity, Long> {

    /**
     * Finds all verification logs for a domain.
     */
    List<DomainVerificationLogEntity> findByDomainIdOrderByAttemptedAtDesc(Long domainId);

    /**
     * Finds the most recent verification attempt for a domain.
     */
    DomainVerificationLogEntity findFirstByDomainIdOrderByAttemptedAtDesc(Long domainId);
}
