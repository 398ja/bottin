package xyz.tcheeric.bottin.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-02: Domain Repository Integration Tests.
 * Verifies domain persistence and cascade operations with PostgreSQL.
 */
@Transactional
class DomainRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @BeforeEach
    void setUp() {
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();
    }

    /**
     * Tests that a domain persists with default unverified status.
     */
    @Test
    void shouldPersistDomainWithDefaultUnverifiedStatus() {
        // Arrange: Create a domain without setting verified
        DomainEntity domain = DomainEntity.builder()
                .name("newdomain.com")
                .build();

        // Act: Save the domain
        DomainEntity saved = domainRepository.save(domain);

        // Assert: Verify default values
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("newdomain.com");
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getVerificationToken()).isNull();
        assertThat(saved.getVerificationMethod()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    /**
     * Tests updating domain verification status and token persists correctly.
     */
    @Test
    void shouldUpdateVerificationStatusAndToken() {
        // Arrange: Create and save a domain
        DomainEntity domain = domainRepository.save(
                DomainEntity.builder()
                        .name("verify.com")
                        .build()
        );

        // Act: Update verification status and token
        domain.setVerificationToken("abc123token");
        domain.setVerificationMethod(VerificationMethod.DNS_TXT);
        domain.setVerified(true);
        domain.setVerifiedAt(Instant.now());
        DomainEntity updated = domainRepository.save(domain);

        // Assert: Verify changes persist
        assertThat(updated.getVerificationToken()).isEqualTo("abc123token");
        assertThat(updated.getVerificationMethod()).isEqualTo(VerificationMethod.DNS_TXT);
        assertThat(updated.isVerified()).isTrue();
        assertThat(updated.getVerifiedAt()).isNotNull();

        // Re-fetch from database to confirm
        Optional<DomainEntity> refetched = domainRepository.findById(domain.getId());
        assertThat(refetched).isPresent();
        assertThat(refetched.get().isVerified()).isTrue();
    }

    /**
     * Tests querying domains by name.
     */
    @Test
    void shouldFindDomainByName() {
        // Arrange: Create and save a domain
        domainRepository.save(
                DomainEntity.builder()
                        .name("findme.com")
                        .build()
        );

        // Act: Query by name
        Optional<DomainEntity> found = domainRepository.findByName("findme.com");

        // Assert: Verify the domain is found
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("findme.com");
    }

    /**
     * Tests querying domains by verification status.
     */
    @Test
    void shouldFindDomainsByVerificationStatus() {
        // Arrange: Create verified and unverified domains
        domainRepository.save(DomainEntity.builder().name("verified.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("verified2.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("unverified.com").verified(false).build());

        // Act: Query by verification status
        List<DomainEntity> verified = domainRepository.findByVerifiedTrue();
        List<DomainEntity> unverified = domainRepository.findByVerifiedFalse();

        // Assert: Verify correct filtering
        assertThat(verified).hasSize(2);
        assertThat(unverified).hasSize(1);
        assertThat(verified).extracting(DomainEntity::getName)
                .containsExactlyInAnyOrder("verified.com", "verified2.com");
    }

    /**
     * Tests cascade deletion of associated NIP-05 records when domain is deleted.
     * The relationship is managed through the parent entity to ensure cascade works.
     */
    @Test
    void shouldCascadeDeleteAssociatedNip05Records() {
        // Arrange: Create domain
        DomainEntity domain = DomainEntity.builder()
                .name("cascade.com")
                .verified(true)
                .build();

        // Add NIP-05 records through the parent entity for proper cascade
        Nip05RecordEntity record1 = Nip05RecordEntity.builder()
                .domain(domain)
                .username("user1")
                .pubkey(TEST_PUBKEY)
                .build();
        Nip05RecordEntity record2 = Nip05RecordEntity.builder()
                .domain(domain)
                .username("user2")
                .pubkey("89be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81799")
                .build();

        domain.getNip05Records().add(record1);
        domain.getNip05Records().add(record2);

        // Save the domain with associated records
        domain = domainRepository.save(domain);

        // Verify records exist
        assertThat(domain.getNip05Records()).hasSize(2);

        Long domainId = domain.getId();

        // Act: Delete the domain
        domainRepository.delete(domain);
        domainRepository.flush();

        // Assert: Domain should be deleted
        assertThat(domainRepository.findById(domainId)).isEmpty();
        // Associated records should be deleted via cascade
        assertThat(nip05RecordRepository.findByDomainNameAndEnabledTrue("cascade.com")).isEmpty();
    }

    /**
     * Tests that creating a duplicate domain name fails with constraint violation.
     */
    @Test
    void shouldFailWhenCreatingDuplicateDomainName() {
        // Arrange: Create and save first domain
        domainRepository.save(
                DomainEntity.builder()
                        .name("unique.com")
                        .build()
        );

        // Act & Assert: Creating duplicate should fail
        DomainEntity duplicate = DomainEntity.builder()
                .name("unique.com")
                .build();

        assertThatThrownBy(() -> {
            domainRepository.save(duplicate);
            domainRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Tests that domain names are normalized to lowercase.
     */
    @Test
    void shouldNormalizeDomainNameToLowercase() {
        // Arrange & Act: Create a domain with uppercase name
        DomainEntity domain = domainRepository.save(
                DomainEntity.builder()
                        .name("UPPERCASE.COM")
                        .build()
        );

        // Assert: Domain name should be lowercase
        assertThat(domain.getName()).isEqualTo("uppercase.com");
    }

    /**
     * Tests finding domains with pending verification.
     */
    @Test
    void shouldFindDomainsWithPendingVerification() {
        // Arrange: Create domains with and without verification tokens
        domainRepository.save(
                DomainEntity.builder()
                        .name("pending1.com")
                        .verified(false)
                        .verificationToken("token1")
                        .build()
        );
        domainRepository.save(
                DomainEntity.builder()
                        .name("pending2.com")
                        .verified(false)
                        .verificationToken("token2")
                        .build()
        );
        domainRepository.save(
                DomainEntity.builder()
                        .name("nopending.com")
                        .verified(false)
                        .verificationToken(null)
                        .build()
        );
        domainRepository.save(
                DomainEntity.builder()
                        .name("alreadyverified.com")
                        .verified(true)
                        .verificationToken("oldtoken")
                        .build()
        );

        // Act: Find pending verification
        List<DomainEntity> pending = domainRepository.findPendingVerification();

        // Assert: Only unverified domains with tokens should be returned
        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(DomainEntity::getName)
                .containsExactlyInAnyOrder("pending1.com", "pending2.com");
    }

    /**
     * Tests counting verified and unverified domains.
     */
    @Test
    void shouldCountDomainsByVerificationStatus() {
        // Arrange: Create domains with different statuses
        domainRepository.save(DomainEntity.builder().name("v1.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("v2.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("v3.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("u1.com").verified(false).build());
        domainRepository.save(DomainEntity.builder().name("u2.com").verified(false).build());

        // Act & Assert: Count by status
        assertThat(domainRepository.countByVerifiedTrue()).isEqualTo(3);
        assertThat(domainRepository.countByVerifiedFalse()).isEqualTo(2);
    }

    /**
     * Tests finding domains by owner pubkey.
     */
    @Test
    void shouldFindDomainsByOwnerPubkey() {
        // Arrange: Create domains with different owners
        domainRepository.save(
                DomainEntity.builder()
                        .name("owner1.com")
                        .ownerPubkey(TEST_PUBKEY)
                        .build()
        );
        domainRepository.save(
                DomainEntity.builder()
                        .name("owner2.com")
                        .ownerPubkey(TEST_PUBKEY)
                        .build()
        );
        domainRepository.save(
                DomainEntity.builder()
                        .name("other.com")
                        .ownerPubkey("different-pubkey")
                        .build()
        );

        // Act: Find by owner pubkey
        List<DomainEntity> owned = domainRepository.findByOwnerPubkey(TEST_PUBKEY);

        // Assert: Verify correct filtering
        assertThat(owned).hasSize(2);
        assertThat(owned).extracting(DomainEntity::getName)
                .containsExactlyInAnyOrder("owner1.com", "owner2.com");
    }
}
