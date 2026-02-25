package xyz.tcheeric.bottin.starter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Record;
import xyz.tcheeric.nsecbunker.account.registration.AccountManager;
import xyz.tcheeric.nsecbunker.account.registration.AccountRegistrationResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent implementation of {@link AccountManager} that coordinates
 * NIP-05 setup with database persistence.
 *
 * <p>This implementation uses bottin's PersistentNip05Manager for the underlying
 * NIP-05 record management and provides additional account-level functionality.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersistentAccountManager implements AccountManager {

    private final PersistentNip05Manager nip05Manager;
    private final Nip05RecordRepository nip05RecordRepository;

    @Override
    @Transactional
    public CompletableFuture<AccountRegistrationResult> createAccount(String username, String domain) {
        log.debug("persistent_account_create username={} domain={}", username, domain);

        return nip05Manager.setupNip05(username, domain)
                .thenApply(nip05Record -> toAccountResult(nip05Record, username, domain));
    }

    @Override
    @Transactional
    public CompletableFuture<AccountRegistrationResult> registerAccount(String username, String domain) {
        log.debug("persistent_account_register username={} domain={}", username, domain);

        // For bottin, registerAccount and createAccount are equivalent
        return createAccount(username, domain);
    }

    @Override
    public CompletableFuture<String> generateKeyForAccount(String username) {
        // Generate a key name based on the username
        return CompletableFuture.completedFuture("key-" + username.toLowerCase());
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Optional<AccountRegistrationResult>> findAccount(String nip05) {
        return nip05Manager.findByNip05(nip05)
                .thenApply(opt -> opt.map(record -> toAccountResult(
                        record,
                        record.getUsername(),
                        record.getDomain()
                )));
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Optional<AccountRegistrationResult>> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String normalizedUsername = username.toLowerCase();

            // Find any record with this username (may return multiple if same username on different domains)
            return nip05RecordRepository.findByUsernameContainingIgnoreCase(normalizedUsername,
                    org.springframework.data.domain.PageRequest.of(0, 1))
                    .getContent()
                    .stream()
                    .filter(Nip05RecordEntity::isEnabled)
                    .findFirst()
                    .map(entity -> AccountRegistrationResult.builder()
                            .username(entity.getUsername())
                            .domain(entity.getDomain().getName())
                            .nip05(entity.getNip05())
                            .npub(entity.getPubkey())
                            .keyName("key-" + entity.getUsername())
                            .createdAt(entity.getCreatedAt())
                            .build());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<List<AccountRegistrationResult>> findByDomain(String domain) {
        return nip05Manager.findByDomain(domain)
                .thenApply(records -> records.stream()
                        .map(record -> toAccountResult(record, record.getUsername(), record.getDomain()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Boolean> exists(String nip05) {
        return nip05Manager.verifyNip05(nip05);
    }

    @Override
    @Transactional
    public CompletableFuture<Boolean> deleteAccount(String nip05) {
        log.debug("persistent_account_delete nip05={}", nip05);
        return nip05Manager.deleteNip05(nip05);
    }

    @Override
    @Transactional(readOnly = true)
    public CompletableFuture<Long> count() {
        return nip05Manager.count();
    }

    /**
     * Converts a Nip05Record to an AccountRegistrationResult.
     */
    private AccountRegistrationResult toAccountResult(Nip05Record record, String username, String domain) {
        return AccountRegistrationResult.builder()
                .username(username)
                .domain(domain)
                .nip05(record.getNip05())
                .npub(record.getPubkey())
                .keyName("key-" + username.toLowerCase())
                .createdAt(Instant.now())
                .build();
    }
}
