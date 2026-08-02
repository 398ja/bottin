package xyz.tcheeric.bottin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.model.AdminUserData;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Maintains the administrators permitted to sign in to the dashboard.
 *
 * <p>Holds ordinary administrators only. The super administrator is the key in
 * deployment configuration and is never stored: a stored copy could disagree
 * with configuration with no correct way to resolve the difference, and keeping
 * it out of the table makes "the master key cannot be removed" true because
 * there is nothing to remove, rather than because a guard remembers to refuse.
 */
@Slf4j
@Service
public class AdminUserService {

    /**
     * What became of an addition.
     *
     * <p>Returning an outcome makes {@code add} both do something and answer
     * something, which Command-Query Separation would rather it did not. The
     * alternative is worse: adding a key that already administers the deployment
     * is not a failure — the state the operator asked for already holds — so it
     * cannot be an exception, and the caller still has to tell the operator
     * which of the two things happened.
     */
    public enum AdditionOutcome {
        ADDED,
        ALREADY_ADMINISTERS
    }

    private final AdminUserRepository repository;

    /**
     * The configured master key in canonical hex, or null when unset or
     * unreadable. Read from the same property the ACL resolver reads, so there
     * is one configured value with two readers rather than two configurations.
     */
    private final String masterKeyHex;

    public AdminUserService(AdminUserRepository repository,
                            @Value("${bottin.admin.npub:}") String configuredMasterKey) {
        this.repository = repository;
        this.masterKeyHex = NostrPublicKeys.toCanonicalHex(configuredMasterKey).orElse(null);
    }

    /**
     * The configured super administrator's key in canonical hex, or empty when
     * the deployment has none or the configured value is not a key.
     *
     * <p>Offered so the settings page can show who the super administrator is
     * without reading configuration a second time and reaching a different
     * answer.
     */
    public Optional<String> superAdministratorKey() {
        return Optional.ofNullable(masterKeyHex);
    }

    @Transactional(readOnly = true)
    public List<AdminUserData> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream()
                .map(AdminUserEntity::toAdminUserData)
                .toList();
    }

    /**
     * Whether a proven key is an administrator. The question the ACL resolver
     * asks on every sign-in.
     *
     * @param pubkeyHex canonical lowercase hex
     */
    @Transactional(readOnly = true)
    public boolean isAdministrator(String pubkeyHex) {
        return repository.findByPubkey(pubkeyHex)
                .filter(AdminUserEntity::isEnabled)
                .isPresent();
    }

    /**
     * Adds an administrator, or reports that the key already administers the
     * deployment.
     *
     * <p>Adding is idempotent: the same key any number of times leaves exactly
     * one entry, and the configured master key leaves none. Nothing is written
     * in either case — not written and then removed.
     *
     * @param keyInput      a public key as {@code npub1…} or 64-character hex
     * @param label         optional human-readable description
     * @param addedByPubkey the administrator performing the addition, canonical hex
     * @throws IllegalArgumentException if the value is not a public key
     */
    @Transactional
    public AdditionOutcome add(String keyInput, String label, String addedByPubkey) {
        String pubkey = canonicalOrReject(keyInput);

        if (alreadyAdministers(pubkey)) {
            log.info("administrator_add_ignored reason={} pubkey={} attempted_by={}",
                    reasonAlreadyAdministers(pubkey), pubkey, addedByPubkey);
            return AdditionOutcome.ALREADY_ADMINISTERS;
        }

        repository.save(AdminUserEntity.fromAdminUserData(
                AdminUserData.createNew(pubkey, blankToNull(label), addedByPubkey)));

        log.info("administrator_added pubkey={} added_by={} label_present={}",
                pubkey, addedByPubkey, blankToNull(label) != null);
        return AdditionOutcome.ADDED;
    }

    private String canonicalOrReject(String keyInput) {
        Optional<String> canonical = NostrPublicKeys.toCanonicalHex(keyInput);
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException(
                    "Administrator not added. '" + keyInput + "' is not a Nostr public key. "
                            + "Suggestion: enter the key as npub1... or as 64 hexadecimal characters.");
        }
        return canonical.get();
    }

    /**
     * Whether this key can already administer the deployment, by either route.
     * The master key is checked first because it is authoritative and costs no
     * query.
     */
    private boolean alreadyAdministers(String pubkey) {
        return isMasterKey(pubkey) || repository.existsByPubkey(pubkey);
    }

    private boolean isMasterKey(String pubkey) {
        return masterKeyHex != null && masterKeyHex.equals(pubkey);
    }

    private String reasonAlreadyAdministers(String pubkey) {
        return isMasterKey(pubkey) ? "already_super_admin" : "already_administrator";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
