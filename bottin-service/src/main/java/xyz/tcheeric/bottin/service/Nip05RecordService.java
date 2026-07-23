package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.exception.DomainNotVerifiedException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordExistsException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordNotFoundException;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing NIP-05 records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Nip05RecordService {

    private final Nip05RecordRepository nip05RecordRepository;
    private final DomainRepository domainRepository;
    private final ObjectMapper objectMapper;
    private final Nip05RecordProperties recordProperties;

    /**
     * Retrieves all records with pagination.
     */
    @Transactional(readOnly = true)
    public Page<Nip05RecordData> findAll(Pageable pageable) {
        return nip05RecordRepository.findAll(pageable)
                .map(Nip05RecordEntity::toNip05RecordData);
    }

    /**
     * Retrieves a record by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Nip05RecordData> findById(Long id) {
        return nip05RecordRepository.findById(id)
                .map(Nip05RecordEntity::toNip05RecordData);
    }

    /**
     * Retrieves a record by NIP-05 identifier.
     */
    @Transactional(readOnly = true)
    public Optional<Nip05RecordData> findByNip05(String nip05) {
        String[] parts = nip05.split("@");
        if (parts.length != 2) {
            return Optional.empty();
        }
        return nip05RecordRepository.findByUsernameAndDomainName(parts[0].toLowerCase(), parts[1].toLowerCase())
                .map(Nip05RecordEntity::toNip05RecordData);
    }

    /**
     * Creates a new NIP-05 record.
     */
    @Transactional
    public Nip05RecordData create(String username, String domain, String pubkey, List<String> relays) {
        String normalizedUsername = username.toLowerCase();
        String normalizedDomain = domain.toLowerCase();

        log.debug("nip05_record_create_attempt username={} domain={} pubkey={}",
                normalizedUsername, normalizedDomain, pubkey);

        DomainEntity domainEntity = domainRepository.findByName(normalizedDomain)
                .orElseThrow(() -> new DomainNotFoundException(normalizedDomain));

        if (!domainEntity.isVerified()) {
            throw new DomainNotVerifiedException(normalizedDomain);
        }

        if (nip05RecordRepository.existsByUsernameAndDomain(normalizedUsername, domainEntity)) {
            throw new Nip05RecordExistsException(normalizedUsername + "@" + normalizedDomain);
        }

        String relaysJson = serializeRelays(mergeWithDefaults(relays));

        Nip05RecordEntity entity = Nip05RecordEntity.builder()
                .domain(domainEntity)
                .username(normalizedUsername)
                .pubkey(pubkey.toLowerCase())
                .relaysJson(relaysJson)
                .enabled(true)
                .build();

        entity = nip05RecordRepository.save(entity);

        log.info("nip05_record_created id={} nip05={}@{} pubkey={}",
                entity.getId(), normalizedUsername, normalizedDomain, pubkey);

        return entity.toNip05RecordData();
    }

    /**
     * Updates an existing NIP-05 record.
     */
    @Transactional
    public Nip05RecordData update(Long id, String pubkey, List<String> relays, Boolean enabled) {
        Nip05RecordEntity entity = nip05RecordRepository.findById(id)
                .orElseThrow(() -> new Nip05RecordNotFoundException(id));

        log.debug("nip05_record_update_attempt id={} nip05={}", id, entity.getNip05());

        if (pubkey != null) {
            entity.setPubkey(pubkey.toLowerCase());
        }

        if (relays != null) {
            entity.setRelaysJson(serializeRelays(relays));
        }

        if (enabled != null) {
            entity.setEnabled(enabled);
        }

        entity = nip05RecordRepository.save(entity);

        log.info("nip05_record_updated id={} nip05={}", id, entity.getNip05());

        return entity.toNip05RecordData();
    }

    /**
     * Toggles the enabled status of a record.
     */
    @Transactional
    public Nip05RecordData toggleEnabled(Long id) {
        Nip05RecordEntity entity = nip05RecordRepository.findById(id)
                .orElseThrow(() -> new Nip05RecordNotFoundException(id));

        boolean newStatus = !entity.isEnabled();
        entity.setEnabled(newStatus);
        entity = nip05RecordRepository.save(entity);

        log.info("nip05_record_toggled id={} nip05={} enabled={}", id, entity.getNip05(), newStatus);

        return entity.toNip05RecordData();
    }

    /**
     * Deletes a record by ID.
     */
    @Transactional
    public void delete(Long id) {
        Nip05RecordEntity entity = nip05RecordRepository.findById(id)
                .orElseThrow(() -> new Nip05RecordNotFoundException(id));

        String nip05 = entity.getNip05();
        nip05RecordRepository.delete(entity);

        log.info("nip05_record_deleted id={} nip05={}", id, nip05);
    }

    /**
     * Searches records by username.
     */
    @Transactional(readOnly = true)
    public Page<Nip05RecordData> searchByUsername(String username, Pageable pageable) {
        return nip05RecordRepository.findByUsernameContainingIgnoreCase(username, pageable)
                .map(Nip05RecordEntity::toNip05RecordData);
    }

    /**
     * Finds records by domain.
     */
    @Transactional(readOnly = true)
    public Page<Nip05RecordData> findByDomain(String domain, Pageable pageable) {
        return nip05RecordRepository.findByDomainName(domain.toLowerCase(), pageable)
                .map(Nip05RecordEntity::toNip05RecordData);
    }

    /**
     * Merges the app's configured default relays (union, de-duplicated,
     * app-relays-first) with any caller-supplied relays. Guarantees every
     * created record advertises the deployment's own relay while preserving
     * whatever the caller sent. Configured defaults are empty by default, so
     * with no configuration this is a no-op passthrough of {@code callerRelays}.
     */
    List<String> mergeWithDefaults(List<String> callerRelays) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        List<String> defaults = recordProperties.getDefaultRelays();
        if (defaults != null) {
            merged.addAll(defaults);
        }
        if (callerRelays != null) {
            merged.addAll(callerRelays);
        }
        return new java.util.ArrayList<>(merged);
    }

    private String serializeRelays(List<String> relays) {
        if (relays == null || relays.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(relays);
        } catch (JsonProcessingException e) {
            log.warn("nip05_relay_serialize_failed error={}", e.getMessage());
            return "[]";
        }
    }
}
