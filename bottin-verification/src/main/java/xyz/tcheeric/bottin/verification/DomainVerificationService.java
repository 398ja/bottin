package xyz.tcheeric.bottin.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.DomainVerificationLogEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.DomainVerificationLogRepository;

import java.time.Instant;

/**
 * Orchestrates domain verification using DNS TXT or well-known file methods.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainVerificationService {

    private final DomainRepository domainRepository;
    private final DomainVerificationLogRepository verificationLogRepository;
    private final VerificationTokenGenerator tokenGenerator;
    private final DnsVerificationService dnsVerificationService;
    private final WellKnownVerificationService wellKnownVerificationService;

    /**
     * Initiates verification for a domain, generating a token if needed.
     * Does not require a specific method since both DNS and Well-Known are supported.
     *
     * @param domainId the domain ID
     * @return the verification challenge with setup instructions
     */
    @Transactional
    public VerificationChallenge initiateVerification(Long domainId) {
        return initiateVerification(domainId, VerificationMethod.DNS_TXT);
    }

    /**
     * Initiates verification for a domain using the specified method.
     *
     * @param domainId the domain ID
     * @param method the verification method to use (stored for logging purposes)
     * @return the verification challenge with setup instructions
     */
    @Transactional
    public VerificationChallenge initiateVerification(Long domainId, VerificationMethod method) {
        DomainEntity domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException(domainId.toString()));

        if (domain.isVerified()) {
            log.debug("domain_already_verified id={} name={}", domainId, domain.getName());
            return VerificationChallenge.alreadyVerified(domain.getName());
        }

        String token = tokenGenerator.generateToken();

        domain.setVerificationMethod(method);
        domain.setVerificationToken(token);
        domain.setUpdatedAt(Instant.now());
        domainRepository.save(domain);

        String instructions = switch (method) {
            case DNS_TXT -> dnsVerificationService.getSetupInstructions(domain.getName(), token);
            case WELL_KNOWN_FILE -> wellKnownVerificationService.getSetupInstructions(domain.getName(), token);
        };

        log.info("domain_verification_initiated id={} name={} method={}",
                domainId, domain.getName(), method);

        return VerificationChallenge.builder()
                .domainId(domainId)
                .domainName(domain.getName())
                .method(method)
                .token(token)
                .instructions(instructions)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Attempts to verify a domain by trying both DNS and Well-Known methods.
     * Succeeds if either method validates the token.
     *
     * @param domainId the domain ID
     * @return the verification result
     */
    @Transactional
    public VerificationResult attemptVerification(Long domainId) {
        DomainEntity domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException(domainId.toString()));

        if (domain.isVerified()) {
            return VerificationResult.success(
                    domain.getVerificationMethod(),
                    "Domain is already verified"
            );
        }

        if (domain.getVerificationToken() == null) {
            return VerificationResult.failure(
                    null,
                    "Verification has not been initiated. Call initiate first."
            );
        }

        log.debug("domain_verification_attempt id={} name={}", domainId, domain.getName());

        // Try DNS verification first
        VerificationResult dnsResult = dnsVerificationService.verify(
                domain.getName(),
                domain.getVerificationToken()
        );

        if (dnsResult.isSuccess()) {
            domain.setVerificationMethod(VerificationMethod.DNS_TXT);
            logVerificationAttempt(domain, dnsResult);
            markDomainVerified(domain);
            log.info("domain_verified id={} name={} method=DNS_TXT", domainId, domain.getName());
            return dnsResult;
        }

        // Try Well-Known verification if DNS failed
        VerificationResult wellKnownResult = wellKnownVerificationService.verify(
                domain.getName(),
                domain.getVerificationToken()
        );

        if (wellKnownResult.isSuccess()) {
            domain.setVerificationMethod(VerificationMethod.WELL_KNOWN_FILE);
            logVerificationAttempt(domain, wellKnownResult);
            markDomainVerified(domain);
            log.info("domain_verified id={} name={} method=WELL_KNOWN_FILE", domainId, domain.getName());
            return wellKnownResult;
        }

        // Both methods failed
        logVerificationAttempt(domain, dnsResult);
        log.debug("domain_verification_failed id={} name={} dns_error={} wellknown_error={}",
                domainId, domain.getName(), dnsResult.getMessage(), wellKnownResult.getMessage());

        return VerificationResult.failure(
                null,
                "Verification failed. DNS: " + dnsResult.getMessage() + ". Well-Known: " + wellKnownResult.getMessage()
        );
    }

    private void markDomainVerified(DomainEntity domain) {
        domain.setVerified(true);
        domain.setVerifiedAt(Instant.now());
        domain.setVerificationToken(null);
        domain.setUpdatedAt(Instant.now());
        domainRepository.save(domain);
    }

    /**
     * Gets the current verification status for a domain.
     *
     * @param domainId the domain ID
     * @return the verification status
     */
    @Transactional(readOnly = true)
    public VerificationStatus getVerificationStatus(Long domainId) {
        DomainEntity domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException(domainId.toString()));

        return VerificationStatus.builder()
                .domainId(domainId)
                .domainName(domain.getName())
                .verified(domain.isVerified())
                .verifiedAt(domain.getVerifiedAt())
                .method(domain.getVerificationMethod())
                .hasPendingVerification(domain.getVerificationToken() != null)
                .build();
    }

    /**
     * Re-verifies a previously verified domain.
     *
     * @param domainId the domain ID
     * @return the verification result
     */
    @Transactional
    public VerificationResult reverify(Long domainId) {
        DomainEntity domain = domainRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException(domainId.toString()));

        if (!domain.isVerified()) {
            return VerificationResult.failure(
                    null,
                    "Domain is not verified. Use initiateVerification first."
            );
        }

        // For re-verification, we use a new token and the same method
        String token = tokenGenerator.generateToken();
        domain.setVerificationToken(token);
        domain.setUpdatedAt(Instant.now());
        domainRepository.save(domain);

        log.debug("domain_reverification_attempt id={} name={} method={}",
                domainId, domain.getName(), domain.getVerificationMethod());

        VerificationResult result = switch (domain.getVerificationMethod()) {
            case DNS_TXT -> dnsVerificationService.verify(domain.getName(), token);
            case WELL_KNOWN_FILE -> wellKnownVerificationService.verify(domain.getName(), token);
        };

        logVerificationAttempt(domain, result);

        if (!result.isSuccess()) {
            // Re-verification failed, mark domain as unverified
            domain.setVerified(false);
            domain.setVerifiedAt(null);
            domain.setUpdatedAt(Instant.now());
            domainRepository.save(domain);

            log.warn("domain_reverification_failed id={} name={} reason={}",
                    domainId, domain.getName(), result.getMessage());
        } else {
            // Clear token after successful re-verification
            domain.setVerificationToken(null);
            domain.setVerifiedAt(Instant.now());
            domain.setUpdatedAt(Instant.now());
            domainRepository.save(domain);

            log.info("domain_reverified id={} name={}", domainId, domain.getName());
        }

        return result;
    }

    private void logVerificationAttempt(DomainEntity domain, VerificationResult result) {
        DomainVerificationLogEntity logEntry = DomainVerificationLogEntity.builder()
                .domain(domain)
                .method(domain.getVerificationMethod())
                .success(result.isSuccess())
                .message(result.getMessage())
                .build();
        verificationLogRepository.save(logEntry);
    }
}
