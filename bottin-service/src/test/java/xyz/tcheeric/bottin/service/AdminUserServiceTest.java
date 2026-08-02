package xyz.tcheeric.bottin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.core.model.AdminRole;
import xyz.tcheeric.bottin.persistence.entity.AdminUserEntity;
import xyz.tcheeric.bottin.persistence.repository.AdminUserRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdminUserService.
 *
 * <p>This service decides who may administer the deployment, so the tests cover
 * the ways it could be wrong in both directions: storing a key it should not
 * have, and refusing one it should have accepted. The no-op cases assert that
 * nothing was written, not merely that no error was raised — a handler that
 * stored and then deleted would satisfy the weaker claim.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    /** A known keypair: npub and hex below are the same key. */
    private static final String NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6";
    private static final String HEX = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    /** The configured master key for these tests — a different, real key. */
    private static final String MASTER_NPUB = "npub1antwcjptjquv5k2wkh6mkr2gzayzeg046spy97guwu2p9cy2s8ush27znn";
    private static final String MASTER_HEX = "ecd6ec482b9038ca594eb5f5bb0d4817482ca1f5d40242f91c771412e08a81f9";

    private static final String ADDED_BY = MASTER_HEX;

    @Mock
    private AdminUserRepository repository;

    private AdminUserService service(String configuredMasterKey) {
        return new AdminUserService(repository, configuredMasterKey);
    }

    /**
     * Tests that a valid key is stored in the canonical hex form, whatever form
     * it was typed in — the form everything downstream compares and revokes by.
     */
    @Test
    void shouldStoreAnAddedKeyInCanonicalHexForm() {
        // Given: a deployment with no administrator holding this key
        when(repository.existsByPubkey(HEX)).thenReturn(false);
        AdminUserService service = service(MASTER_NPUB);

        // When: the key is added in bech32 form
        AdminUserService.AdditionOutcome outcome = service.add(NPUB, "Ops laptop", ADDED_BY);

        // Then: it is stored as hex
        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ADDED);
        ArgumentCaptor<AdminUserEntity> saved = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPubkey()).isEqualTo(HEX);
    }

    /**
     * Tests that the administrator who performed the addition is recorded, so a
     * change of access is attributable.
     */
    @Test
    void shouldRecordWhoAddedTheAdministrator() {
        when(repository.existsByPubkey(HEX)).thenReturn(false);

        service(MASTER_NPUB).add(HEX, "Ops laptop", ADDED_BY);

        ArgumentCaptor<AdminUserEntity> saved = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAddedByPubkey()).isEqualTo(ADDED_BY);
        assertThat(saved.getValue().getLabel()).isEqualTo("Ops laptop");
        assertThat(saved.getValue().getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    /**
     * Tests that a key already administering the deployment is not added a
     * second time, and that this is reported rather than raised as a failure —
     * the state the operator asked for already holds.
     */
    @Test
    void shouldNotStoreAKeyThatIsAlreadyAnAdministrator() {
        // Given: the key is already stored
        when(repository.existsByPubkey(HEX)).thenReturn(true);

        // When: it is added again
        AdminUserService.AdditionOutcome outcome = service(MASTER_NPUB).add(HEX, "Duplicate", ADDED_BY);

        // Then: nothing is written and the outcome says so
        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ALREADY_ADMINISTERS);
        verify(repository, never()).save(any());
    }

    /**
     * Tests that the same key entered as npub and as hex is one administrator.
     * This is the whole reason the input is canonicalised before comparison.
     */
    @Test
    void shouldTreatNpubAndHexOfOneKeyAsOneAdministrator() {
        when(repository.existsByPubkey(HEX)).thenReturn(true);

        AdminUserService.AdditionOutcome outcome = service(MASTER_NPUB).add(NPUB, null, ADDED_BY);

        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ALREADY_ADMINISTERS);
        verify(repository, never()).save(any());
    }

    /**
     * Tests that adding the configured master key stores nothing. It already
     * administers the deployment as super administrator, and a lesser stored
     * entry for it could only ever be misleading.
     */
    @Test
    void shouldNotStoreTheConfiguredMasterKey() {
        // Given: a deployment whose master key is MASTER
        AdminUserService service = service(MASTER_NPUB);

        // When: the master key is offered as an ordinary administrator
        AdminUserService.AdditionOutcome outcome = service.add(MASTER_HEX, "Me", ADDED_BY);

        // Then: nothing is written — not even briefly
        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ALREADY_ADMINISTERS);
        verify(repository, never()).save(any());
    }

    /**
     * Tests that the master key is recognised however it is written, so its
     * bech32 form cannot slip past the check that its hex form fails.
     */
    @Test
    void shouldRecogniseTheMasterKeyInEitherEncoding() {
        AdminUserService.AdditionOutcome outcome = service(MASTER_HEX).add(MASTER_NPUB, "Me", ADDED_BY);

        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ALREADY_ADMINISTERS);
        verify(repository, never()).save(any());
    }

    /**
     * Tests that a value which is not a public key is refused with the offending
     * value named, as the settings page already does for relay URLs.
     */
    @Test
    void shouldRejectAValueThatIsNotAPublicKeyNamingIt() {
        assertThatThrownBy(() -> service(MASTER_NPUB).add("not-a-key", "Nonsense", ADDED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-key");

        verify(repository, never()).save(any());
    }

    /**
     * Tests that an empty submission is refused rather than stored as a blank
     * administrator.
     */
    @Test
    void shouldRejectABlankKey() {
        assertThatThrownBy(() -> service(MASTER_NPUB).add("   ", null, ADDED_BY))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    /**
     * Tests that an unconfigured deployment still stores added administrators.
     * With no master key there is nobody to compare against, and refusing here
     * would make a misconfiguration also a data problem.
     */
    @Test
    void shouldStillAddWhenNoMasterKeyIsConfigured() {
        when(repository.existsByPubkey(HEX)).thenReturn(false);

        AdminUserService.AdditionOutcome outcome = service("").add(HEX, "Ops", ADDED_BY);

        assertThat(outcome).isEqualTo(AdminUserService.AdditionOutcome.ADDED);
        verify(repository).save(any());
    }

    /**
     * Tests that the list is returned oldest first, so it does not reorder
     * itself between visits.
     */
    @Test
    void shouldListAdministratorsOldestFirst() {
        AdminUserEntity first = AdminUserEntity.builder()
                .pubkey(HEX).label("First").role(AdminRole.ADMIN).enabled(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        AdminUserEntity second = AdminUserEntity.builder()
                .pubkey(MASTER_HEX).label("Second").role(AdminRole.ADMIN).enabled(true)
                .createdAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(first, second));

        assertThat(service(MASTER_NPUB).list())
                .extracting("label")
                .containsExactly("First", "Second");
    }

    /**
     * Tests the question the ACL resolver asks on every sign-in: is this proven
     * key an administrator?
     */
    @Test
    void shouldRecogniseAStoredAdministrator() {
        when(repository.findByPubkey(HEX)).thenReturn(java.util.Optional.of(
                AdminUserEntity.builder().pubkey(HEX).role(AdminRole.ADMIN).enabled(true).build()));

        assertThat(service(MASTER_NPUB).isAdministrator(HEX)).isTrue();
    }

    /**
     * Tests that a disabled administrator is not admitted. The column exists so
     * suspension can be added later; until then a disabled row must not be a way in.
     */
    @Test
    void shouldRefuseADisabledAdministrator() {
        when(repository.findByPubkey(HEX)).thenReturn(java.util.Optional.of(
                AdminUserEntity.builder().pubkey(HEX).role(AdminRole.ADMIN).enabled(false).build()));

        assertThat(service(MASTER_NPUB).isAdministrator(HEX)).isFalse();
    }

    /**
     * Tests that an unknown key is not an administrator.
     */
    @Test
    void shouldRefuseAnUnknownKey() {
        when(repository.findByPubkey(HEX)).thenReturn(java.util.Optional.empty());

        assertThat(service(MASTER_NPUB).isAdministrator(HEX)).isFalse();
    }
}
