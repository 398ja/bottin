package xyz.tcheeric.bottin.admin.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.List;

/**
 * Service for admin dashboard operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final Nip05RecordRepository recordRepository;
    private final DomainRepository domainRepository;

    /**
     * Gets dashboard statistics.
     */
    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats() {
        long totalRecords = recordRepository.count();
        long enabledRecords = recordRepository.countByEnabledTrue();
        long totalDomains = domainRepository.count();
        long verifiedDomains = domainRepository.countByVerifiedTrue();
        long pendingVerification = domainRepository.countByVerifiedFalse();

        return DashboardStats.builder()
                .totalRecords(totalRecords)
                .enabledRecords(enabledRecords)
                .disabledRecords(totalRecords - enabledRecords)
                .totalDomains(totalDomains)
                .verifiedDomains(verifiedDomains)
                .pendingVerification(pendingVerification)
                .build();
    }

    /**
     * Gets recent NIP-05 records.
     */
    @Transactional(readOnly = true)
    public List<Nip05RecordEntity> getRecentRecords(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Nip05RecordEntity> page = recordRepository.findAll(pageRequest);
        return page.getContent();
    }

    /**
     * Gets recent domains.
     */
    @Transactional(readOnly = true)
    public List<DomainEntity> getRecentDomains(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DomainEntity> page = domainRepository.findAll(pageRequest);
        return page.getContent();
    }

    /**
     * Gets domains pending verification.
     */
    @Transactional(readOnly = true)
    public List<DomainEntity> getPendingVerificationDomains() {
        return domainRepository.findByVerifiedFalse();
    }

    @Value
    @Builder
    public static class DashboardStats {
        long totalRecords;
        long enabledRecords;
        long disabledRecords;
        long totalDomains;
        long verifiedDomains;
        long pendingVerification;
    }
}
