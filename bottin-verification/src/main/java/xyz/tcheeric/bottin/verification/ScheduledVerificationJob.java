package xyz.tcheeric.bottin.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job for periodic re-verification of domains.
 * Runs at configurable intervals to ensure verified domains remain valid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledVerificationJob {

    private final DomainRepository domainRepository;
    private final DomainVerificationService verificationService;

    @Value("${bottin.verification.reverify-after-days:30}")
    private int reverifyAfterDays;

    @Value("${bottin.verification.max-domains-per-run:100}")
    private int maxDomainsPerRun;

    /**
     * Re-verifies domains that were last verified more than reverifyAfterDays ago.
     * Runs daily at 3:00 AM by default.
     */
    @Scheduled(cron = "${bottin.verification.reverify-cron:0 0 3 * * ?}")
    public void reverifyDomains() {
        log.info("scheduled_reverification_started");

        Instant cutoffTime = Instant.now().minus(reverifyAfterDays, ChronoUnit.DAYS);

        List<DomainEntity> domainsToReverify = domainRepository.findDomainsToReverify(
                cutoffTime,
                PageRequest.of(0, maxDomainsPerRun)
        );

        log.info("scheduled_reverification_domains_found count={} cutoff_days={}",
                domainsToReverify.size(), reverifyAfterDays);

        int successCount = 0;
        int failureCount = 0;

        for (DomainEntity domain : domainsToReverify) {
            try {
                VerificationResult result = verificationService.attemptVerification(domain.getId());

                if (result.isSuccess()) {
                    successCount++;
                    log.info("scheduled_reverification_success domain_id={} domain_name={}",
                            domain.getId(), domain.getName());
                } else {
                    failureCount++;
                    log.warn("scheduled_reverification_failed domain_id={} domain_name={} message={}",
                            domain.getId(), domain.getName(), result.getMessage());

                    handleVerificationFailure(domain);
                }
            } catch (Exception e) {
                failureCount++;
                log.error("scheduled_reverification_error domain_id={} domain_name={} error={}",
                        domain.getId(), domain.getName(), e.getMessage(), e);
            }
        }

        log.info("scheduled_reverification_completed total={} success={} failure={}",
                domainsToReverify.size(), successCount, failureCount);
    }

    /**
     * Handles a domain that failed re-verification.
     * Marks the domain as unverified after failing verification.
     */
    private void handleVerificationFailure(DomainEntity domain) {
        log.warn("domain_verification_revoked domain_id={} domain_name={}",
                domain.getId(), domain.getName());

        domain.setVerified(false);
        domain.setVerifiedAt(null);
        domain.setVerificationMethod(null);
        domain.setVerificationToken(null);
        domainRepository.save(domain);
    }

    /**
     * Cleans up expired verification tokens for domains that never completed verification.
     * Runs daily at 4:00 AM by default.
     */
    @Scheduled(cron = "${bottin.verification.cleanup-cron:0 0 4 * * ?}")
    public void cleanupExpiredTokens() {
        log.info("scheduled_token_cleanup_started");

        Instant expirationTime = Instant.now().minus(24, ChronoUnit.HOURS);

        List<DomainEntity> domainsWithExpiredTokens = domainRepository.findDomainsWithExpiredTokens(expirationTime);

        int cleanedCount = 0;
        for (DomainEntity domain : domainsWithExpiredTokens) {
            if (!domain.isVerified() && domain.getVerificationToken() != null) {
                domain.setVerificationToken(null);
                domain.setVerificationMethod(null);
                domainRepository.save(domain);
                cleanedCount++;

                log.debug("verification_token_cleaned domain_id={} domain_name={}",
                        domain.getId(), domain.getName());
            }
        }

        log.info("scheduled_token_cleanup_completed cleaned_count={}", cleanedCount);
    }
}
