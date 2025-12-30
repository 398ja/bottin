package xyz.tcheeric.bottin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing domains.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainService {

    private final DomainRepository domainRepository;
    private final Nip05RecordRepository nip05RecordRepository;

    /**
     * Retrieves all domains with pagination.
     */
    @Transactional(readOnly = true)
    public Page<DomainData> findAll(Pageable pageable) {
        return domainRepository.findAll(pageable)
                .map(this::toDomainDataWithCount);
    }

    /**
     * Retrieves all domains as a list.
     */
    @Transactional(readOnly = true)
    public List<DomainData> findAll() {
        return domainRepository.findAll().stream()
                .map(this::toDomainDataWithCount)
                .toList();
    }

    /**
     * Retrieves a domain by ID.
     */
    @Transactional(readOnly = true)
    public Optional<DomainData> findById(Long id) {
        return domainRepository.findById(id)
                .map(this::toDomainDataWithCount);
    }

    /**
     * Retrieves a domain by name.
     */
    @Transactional(readOnly = true)
    public Optional<DomainData> findByName(String name) {
        return domainRepository.findByName(name.toLowerCase())
                .map(this::toDomainDataWithCount);
    }

    /**
     * Creates a new domain.
     */
    @Transactional
    public DomainData create(String name, String ownerPubkey) {
        String normalizedName = name.toLowerCase();

        log.debug("domain_create_attempt name={} owner_pubkey={}", normalizedName, ownerPubkey);

        if (domainRepository.existsByName(normalizedName)) {
            throw new IllegalArgumentException("Domain already exists: " + normalizedName);
        }

        DomainEntity entity = DomainEntity.builder()
                .name(normalizedName)
                .verified(false)
                .ownerPubkey(ownerPubkey != null ? ownerPubkey.toLowerCase() : null)
                .build();

        entity = domainRepository.save(entity);

        log.info("domain_created id={} name={}", entity.getId(), normalizedName);

        return toDomainDataWithCount(entity);
    }

    /**
     * Deletes a domain by ID.
     * This will also delete all associated NIP-05 records due to cascade.
     */
    @Transactional
    public void delete(Long id) {
        DomainEntity entity = domainRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException(id.toString()));

        String name = entity.getName();
        long recordCount = nip05RecordRepository.countByDomain(entity);

        if (recordCount > 0) {
            log.warn("domain_delete_with_records id={} name={} record_count={}",
                    id, name, recordCount);
        }

        domainRepository.delete(entity);

        log.info("domain_deleted id={} name={} records_deleted={}", id, name, recordCount);
    }

    /**
     * Retrieves all verified domains.
     */
    @Transactional(readOnly = true)
    public List<DomainData> findVerified() {
        return domainRepository.findByVerifiedTrue().stream()
                .map(this::toDomainDataWithCount)
                .toList();
    }

    /**
     * Retrieves all unverified domains.
     */
    @Transactional(readOnly = true)
    public List<DomainData> findUnverified() {
        return domainRepository.findByVerifiedFalse().stream()
                .map(this::toDomainDataWithCount)
                .toList();
    }

    /**
     * Checks if a domain exists.
     */
    @Transactional(readOnly = true)
    public boolean exists(String name) {
        return domainRepository.existsByName(name.toLowerCase());
    }

    private DomainData toDomainDataWithCount(DomainEntity entity) {
        long recordCount = nip05RecordRepository.countByDomain(entity);
        return entity.toDomainData().toBuilder()
                .recordCount((int) recordCount)
                .build();
    }
}
