package xyz.tcheeric.bottin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.AdministratorNotFoundException;
import xyz.tcheeric.bottin.core.model.AdminUserData;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;
import xyz.tcheeric.bottin.service.port.AdministratorSessionRevoker;

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

    /**
     * The session revoker, if this application has one.
     *
     * <p>An {@code ObjectProvider} rather than a plain dependency because this
     * service is component-scanned by every bottin application, while only the
     * dashboard holds sessions. Required outright, it stopped {@code bottin-api}
     * from starting at all — for a bean that application has no use for.
     *
     * <p>Absence is not treated as "nothing to revoke". Removal fails loudly
     * instead, because a silent no-op here is exactly what this port exists to
     * prevent: an application that grew an administrator-removal path would
     * appear to revoke sessions and would not.
     */
    private final ObjectProvider<AdministratorSessionRevoker> sessionRevoker;

    public AdminUserService(AdminUserRepository repository,
                            ObjectProvider<AdministratorSessionRevoker> sessionRevoker,
                            @Value("${bottin.admin.npub:}") String configuredMasterKey) {
        this.repository = repository;
        this.sessionRevoker = sessionRevoker;
        this.masterKeyHex = NostrPublicKeys.toCanonicalHex(configuredMasterKey).orElse(null);
    }

    private AdministratorSessionRevoker sessionRevoker() {
        AdministratorSessionRevoker revoker = sessionRevoker.getIfAvailable();
        if (revoker == null) {
            throw new IllegalStateException(
                    "Cannot end administrator sessions. This application holds no session store, so a "
                            + "removed administrator's session could not be ended here. Suggestion: remove "
                            + "administrators from the admin dashboard, which owns the sessions.");
        }
        return revoker;
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

    /**
     * Removes an administrator and ends every session they hold.
     *
     * <p>The two are one operation deliberately. Removal that leaves a live
     * session is not removal, and separating them would let a future caller do
     * the first without the second — the failure being invisible until somebody
     * kept working after being removed.
     *
     * <p>Deleting before revoking, rather than the reverse: revoking first would
     * leave a window in which the administrator, still stored, could sign in
     * again and obtain a session that outlives the removal.
     *
     * <p>Removing a key that is not an administrator is refused rather than
     * treated as already achieved, because the two ways to arrive there — a
     * stale page and a mistyped key — are both worth seeing.
     *
     * @throws IllegalArgumentException          if the value is not a public key, or is
     *                                           the configured master key
     * @throws AdministratorNotFoundException    if no administrator holds that key
     */
    @Transactional
    public void remove(String pubkey, String removedByPubkey) {
        String canonical = canonicalOrRejectRemoval(pubkey);

        if (isMasterKey(canonical)) {
            log.warn("administrator_change_rejected reason=master_key_immutable pubkey={} attempted_by={}",
                    canonical, removedByPubkey);
            throw new IllegalArgumentException(
                    "Administrator not removed. That key is the super administrator, which is set in "
                            + "deployment configuration. Suggestion: change BOTTIN_ADMIN_NPUB and restart "
                            + "to move the super administrator to another key.");
        }

        if (!repository.existsByPubkey(canonical)) {
            throw new AdministratorNotFoundException(canonical);
        }

        repository.deleteByPubkey(canonical);
        int revoked = sessionRevoker().revokeSessionsFor(canonical);

        log.info("administrator_removed pubkey={} removed_by={} sessions_revoked={}",
                canonical, removedByPubkey, revoked);
    }

    private String canonicalOrRejectRemoval(String keyInput) {
        return NostrPublicKeys.toCanonicalHex(keyInput).orElseThrow(() -> new IllegalArgumentException(
                "Administrator not removed. '" + keyInput + "' is not a Nostr public key. "
                        + "Suggestion: use the remove control beside the administrator in the list."));
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
