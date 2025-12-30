package xyz.tcheeric.bottin.persistence.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for DomainRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class DomainRepositoryTest {

    @Autowired
    private DomainRepository domainRepository;

    /**
     * Tests that a domain can be saved and retrieved by name.
     */
    @Test
    void shouldSaveAndFindByName() {
        // Arrange
        DomainEntity domain = DomainEntity.builder()
                .name("example.com")
                .verified(false)
                .build();

        // Act
        DomainEntity saved = domainRepository.save(domain);
        Optional<DomainEntity> found = domainRepository.findByName("example.com");

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("example.com");
        assertThat(found.get().isVerified()).isFalse();
    }

    /**
     * Tests that existsByName returns true for existing domains.
     */
    @Test
    void shouldCheckExistsByName() {
        // Arrange
        DomainEntity domain = DomainEntity.builder()
                .name("test.com")
                .verified(true)
                .build();
        domainRepository.save(domain);

        // Act & Assert
        assertThat(domainRepository.existsByName("test.com")).isTrue();
        assertThat(domainRepository.existsByName("unknown.com")).isFalse();
    }

    /**
     * Tests finding verified domains.
     */
    @Test
    void shouldFindVerifiedDomains() {
        // Arrange
        DomainEntity verified1 = DomainEntity.builder()
                .name("verified1.com")
                .verified(true)
                .build();
        DomainEntity verified2 = DomainEntity.builder()
                .name("verified2.com")
                .verified(true)
                .build();
        DomainEntity unverified = DomainEntity.builder()
                .name("unverified.com")
                .verified(false)
                .build();

        domainRepository.save(verified1);
        domainRepository.save(verified2);
        domainRepository.save(unverified);

        // Act
        var verifiedDomains = domainRepository.findByVerifiedTrue();

        // Assert
        assertThat(verifiedDomains).hasSize(2);
        assertThat(verifiedDomains).extracting("name")
                .containsExactlyInAnyOrder("verified1.com", "verified2.com");
    }

    /**
     * Tests that verification token is stored correctly.
     */
    @Test
    void shouldStoreVerificationToken() {
        // Arrange
        DomainEntity domain = DomainEntity.builder()
                .name("pending.com")
                .verified(false)
                .verificationToken("abc123xyz")
                .verificationMethod(VerificationMethod.DNS_TXT)
                .build();

        // Act
        domainRepository.save(domain);
        Optional<DomainEntity> found = domainRepository.findByName("pending.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getVerificationToken()).isEqualTo("abc123xyz");
        assertThat(found.get().getVerificationMethod()).isEqualTo(VerificationMethod.DNS_TXT);
    }

    /**
     * Tests finding domains with pending verification.
     */
    @Test
    void shouldFindPendingVerification() {
        // Arrange
        DomainEntity pending = DomainEntity.builder()
                .name("pending.com")
                .verified(false)
                .verificationToken("token123")
                .build();
        DomainEntity verified = DomainEntity.builder()
                .name("verified.com")
                .verified(true)
                .build();
        DomainEntity noPending = DomainEntity.builder()
                .name("no-pending.com")
                .verified(false)
                .build();

        domainRepository.save(pending);
        domainRepository.save(verified);
        domainRepository.save(noPending);

        // Act
        var pendingDomains = domainRepository.findPendingVerification();

        // Assert
        assertThat(pendingDomains).hasSize(1);
        assertThat(pendingDomains.get(0).getName()).isEqualTo("pending.com");
    }

    /**
     * Tests counting verified domains.
     */
    @Test
    void shouldCountVerifiedDomains() {
        // Arrange
        domainRepository.save(DomainEntity.builder().name("v1.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("v2.com").verified(true).build());
        domainRepository.save(DomainEntity.builder().name("uv.com").verified(false).build());

        // Act & Assert
        assertThat(domainRepository.countByVerifiedTrue()).isEqualTo(2);
        assertThat(domainRepository.countByVerifiedFalse()).isEqualTo(1);
    }

    /**
     * Tests that domain name is lowercased on save.
     */
    @Test
    void shouldLowercaseDomainName() {
        // Arrange
        DomainEntity domain = DomainEntity.builder()
                .name("EXAMPLE.COM")
                .build();

        // Act
        DomainEntity saved = domainRepository.save(domain);

        // Assert
        assertThat(saved.getName()).isEqualTo("example.com");
    }

    /**
     * Tests that timestamps are set on persist.
     */
    @Test
    void shouldSetTimestampsOnPersist() {
        // Arrange
        DomainEntity domain = DomainEntity.builder()
                .name("timestamp.com")
                .build();

        // Act
        DomainEntity saved = domainRepository.save(domain);

        // Assert
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
