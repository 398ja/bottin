package xyz.tcheeric.bottin.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nostr.id.Identity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.exception.DomainNotVerifiedException;
import xyz.tcheeric.bottin.core.exception.Nip05RecordExistsException;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent implementation of {@link Nip05Manager} that stores records in the database.
 *
 * <p>This implementation replaces the in-memory DefaultNip05Manager from nsecbunker-account
 * with database-backed persistence via bottin's JPA repositories.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentNip05Manager implements Nip05Manager {

    private final Nip05RecordRepository nip05RecordRepository;
    private final DomainRepository domainRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CompletableFuture<Nip05Record> setupNip05(String username, String domain) {
        return CompletableFuture.supplyAsync(() -> {
            String normalizedUsername = username.toLowerCase();
            String normalizedDomain = domain.toLowerCase();

            log.debug("persistent_nip05_setup username={} domain={}", normalizedUsername, normalizedDomain);

            // Validate domain is registered and verified
            DomainEntity domainEntity = domainRepository.findByName(normalizedDomain)
                    .orElseThrow(() -> new DomainNotFoundException(normalizedDomain));

            if (!domainEntity.isVerified()) {
                throw new DomainNotVerifiedException(normalizedDomain);
            }

            // Check for existing record
            if (nip05RecordRepository.existsByUsernameAndDomain(normalizedUsername, domainEntity)) {
                throw new Nip05RecordExistsException(normalizedUsername + "@" + normalizedDomain);
            }

            // Generate new identity using nostr-java
            Identity identity = Identity.generateRandomIdentity();
            String pubkey = identity.getPublicKey().toString();

            // Create and persist record
            Nip05RecordEntity entity = Nip05RecordEntity.builder()
                    .domain(domainEntity)
                    .username(normalizedUsername)
                    .pubkey(pubkey)
                    .relaysJson("[]")
                    .enabled(true)
                    .build();

            entity = nip05RecordRepository.save(entity);

            log.info("persistent_nip05_created nip05={}@{} pubkey={}",
                    normalizedUsername, normalizedDomain, pubkey);

            return toNip05Record(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Boolean> verifyNip05(String nip05) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = nip05.split("@");
            if (parts.length != 2) {
                return false;
            }

            String username = parts[0].toLowerCase();
            String domain = parts[1].toLowerCase();

            return nip05RecordRepository.findByUsernameAndDomainName(username, domain)
                    .map(Nip05RecordEntity::isEnabled)
                    .orElse(false);
        });
    }

    @Override
    public String generateWellKnown(Nip05Record record) {
        try {
            Map<String, Object> response = new HashMap<>();

            Map<String, String> names = new HashMap<>();
            names.put(record.getUsername(), record.getPubkey());
            response.put("names", names);

            if (record.hasRelays()) {
                Map<String, List<String>> relays = new HashMap<>();
                relays.put(record.getPubkey(), record.getRelays());
                response.put("relays", relays);
            }

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("persistent_nip05_wellknown_generate_failed error={}", e.getMessage());
            return "{}";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Optional<Nip05Record>> findByNip05(String nip05) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = nip05.split("@");
            if (parts.length != 2) {
                return Optional.empty();
            }

            String username = parts[0].toLowerCase();
            String domain = parts[1].toLowerCase();

            return nip05RecordRepository.findByUsernameAndDomainName(username, domain)
                    .filter(Nip05RecordEntity::isEnabled)
                    .map(this::toNip05Record);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<List<Nip05Record>> findByDomain(String domain) {
        return CompletableFuture.supplyAsync(() ->
                nip05RecordRepository.findByDomainNameAndEnabledTrue(domain.toLowerCase())
                        .stream()
                        .map(this::toNip05Record)
                        .toList()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Optional<Nip05Record>> findByPubkey(String pubkey) {
        return CompletableFuture.supplyAsync(() ->
                nip05RecordRepository.findByPubkey(pubkey.toLowerCase())
                        .filter(Nip05RecordEntity::isEnabled)
                        .map(this::toNip05Record)
        );
    }

    @Override
    @Transactional
    public CompletableFuture<Boolean> deleteNip05(String nip05) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = nip05.split("@");
            if (parts.length != 2) {
                return false;
            }

            String username = parts[0].toLowerCase();
            String domain = parts[1].toLowerCase();

            Optional<Nip05RecordEntity> entityOpt = nip05RecordRepository.findByUsernameAndDomainName(username, domain);
            if (entityOpt.isEmpty()) {
                return false;
            }

            nip05RecordRepository.delete(entityOpt.get());
            log.info("persistent_nip05_deleted nip05={}", nip05);
            return true;
        });
    }

    @Override
    @Transactional
    public CompletableFuture<Optional<Nip05Record>> updateRelays(String nip05, List<String> relays) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = nip05.split("@");
            if (parts.length != 2) {
                return Optional.empty();
            }

            String username = parts[0].toLowerCase();
            String domain = parts[1].toLowerCase();

            Optional<Nip05RecordEntity> entityOpt = nip05RecordRepository.findByUsernameAndDomainName(username, domain);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            Nip05RecordEntity entity = entityOpt.get();
            entity.setRelaysJson(serializeRelays(relays));
            entity = nip05RecordRepository.save(entity);

            log.info("persistent_nip05_relays_updated nip05={} relays_count={}", nip05, relays.size());
            return Optional.of(toNip05Record(entity));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Long> count() {
        return CompletableFuture.supplyAsync(() ->
                nip05RecordRepository.countByEnabledTrue()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Long> countByDomain(String domain) {
        return CompletableFuture.supplyAsync(() ->
                nip05RecordRepository.countByDomainNameAndEnabledTrue(domain.toLowerCase())
        );
    }

    /**
     * Converts a JPA entity to a Nip05Record.
     */
    private Nip05Record toNip05Record(Nip05RecordEntity entity) {
        return Nip05Record.builder()
                .nip05(entity.getNip05())
                .pubkey(entity.getPubkey())
                .relaysJson(entity.getRelaysJson())
                .build();
    }

    /**
     * Serializes a list of relay URLs to JSON.
     */
    private String serializeRelays(List<String> relays) {
        if (relays == null || relays.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(relays);
        } catch (JsonProcessingException e) {
            log.warn("persistent_nip05_relay_serialize_failed error={}", e.getMessage());
            return "[]";
        }
    }
}
