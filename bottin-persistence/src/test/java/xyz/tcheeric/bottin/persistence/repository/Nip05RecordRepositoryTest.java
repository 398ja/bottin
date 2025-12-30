package xyz.tcheeric.bottin.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for Nip05RecordRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class Nip05RecordRepositoryTest {

    @Autowired
    private Nip05RecordRepository recordRepository;

    @Autowired
    private DomainRepository domainRepository;

    private DomainEntity testDomain;

    @BeforeEach
    void setUp() {
        testDomain = domainRepository.save(
                DomainEntity.builder()
                        .name("example.com")
                        .verified(true)
                        .build()
        );
    }

    /**
     * Tests that a NIP-05 record can be saved and retrieved.
     */
    @Test
    void shouldSaveAndFindRecord() {
        // Arrange
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("alice")
                .pubkey("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798")
                .relaysJson("[\"wss://relay.example.com\"]")
                .enabled(true)
                .build();

        // Act
        Nip05RecordEntity saved = recordRepository.save(record);
        Optional<Nip05RecordEntity> found = recordRepository.findById(saved.getId());

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getNip05()).isEqualTo("alice@example.com");
    }

    /**
     * Tests finding a record by username and domain.
     */
    @Test
    void shouldFindByUsernameAndDomain() {
        // Arrange
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("bob")
                .pubkey("abcdef1234567890")
                .build();
        recordRepository.save(record);

        // Act
        Optional<Nip05RecordEntity> found = recordRepository.findByUsernameAndDomain("bob", testDomain);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("bob");
    }

    /**
     * Tests finding a record by username and domain name.
     */
    @Test
    void shouldFindByUsernameAndDomainName() {
        // Arrange
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("charlie")
                .pubkey("123456abcdef")
                .build();
        recordRepository.save(record);

        // Act
        Optional<Nip05RecordEntity> found = recordRepository.findByUsernameAndDomainName("charlie", "example.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("charlie");
    }

    /**
     * Tests checking if an enabled record exists.
     */
    @Test
    void shouldCheckEnabledRecordExists() {
        // Arrange
        Nip05RecordEntity enabled = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("enabled")
                .pubkey("pubkey1")
                .enabled(true)
                .build();
        Nip05RecordEntity disabled = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("disabled")
                .pubkey("pubkey2")
                .enabled(false)
                .build();
        recordRepository.save(enabled);
        recordRepository.save(disabled);

        // Act & Assert
        assertThat(recordRepository.existsByUsernameAndDomainNameAndEnabledTrue("enabled", "example.com")).isTrue();
        assertThat(recordRepository.existsByUsernameAndDomainNameAndEnabledTrue("disabled", "example.com")).isFalse();
    }

    /**
     * Tests finding all records for a domain.
     */
    @Test
    void shouldFindByDomain() {
        // Arrange
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("user1").pubkey("pk1").build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("user2").pubkey("pk2").build());

        DomainEntity otherDomain = domainRepository.save(
                DomainEntity.builder().name("other.com").verified(true).build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(otherDomain).username("user3").pubkey("pk3").build());

        // Act
        List<Nip05RecordEntity> records = recordRepository.findByDomain(testDomain);

        // Assert
        assertThat(records).hasSize(2);
        assertThat(records).extracting("username")
                .containsExactlyInAnyOrder("user1", "user2");
    }

    /**
     * Tests finding enabled records for a domain.
     */
    @Test
    void shouldFindEnabledByDomainName() {
        // Arrange
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("e1").pubkey("pk1").enabled(true).build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("e2").pubkey("pk2").enabled(true).build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("d1").pubkey("pk3").enabled(false).build());

        // Act
        List<Nip05RecordEntity> records = recordRepository.findByDomainNameAndEnabledTrue("example.com");

        // Assert
        assertThat(records).hasSize(2);
    }

    /**
     * Tests finding by pubkey.
     */
    @Test
    void shouldFindByPubkey() {
        // Arrange
        String pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("alice").pubkey(pubkey).build());

        // Act
        Optional<Nip05RecordEntity> found = recordRepository.findByPubkey(pubkey);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
    }

    /**
     * Tests counting records by domain.
     */
    @Test
    void shouldCountByDomain() {
        // Arrange
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("u1").pubkey("pk1").build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("u2").pubkey("pk2").build());

        // Act & Assert
        assertThat(recordRepository.countByDomain(testDomain)).isEqualTo(2);
    }

    /**
     * Tests finding recent records.
     */
    @Test
    void shouldFindRecentRecords() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            recordRepository.save(Nip05RecordEntity.builder()
                    .domain(testDomain).username("user" + i).pubkey("pk" + i).build());
        }

        // Act
        List<Nip05RecordEntity> recent = recordRepository.findRecent(PageRequest.of(0, 3));

        // Assert
        assertThat(recent).hasSize(3);
    }

    /**
     * Tests searching by username containing.
     */
    @Test
    void shouldSearchByUsernameContaining() {
        // Arrange
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("alice123").pubkey("pk1").build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("bob_alice").pubkey("pk2").build());
        recordRepository.save(Nip05RecordEntity.builder()
                .domain(testDomain).username("charlie").pubkey("pk3").build());

        // Act
        Page<Nip05RecordEntity> found = recordRepository.findByUsernameContainingIgnoreCase(
                "alice", PageRequest.of(0, 10));

        // Assert
        assertThat(found.getContent()).hasSize(2);
    }

    /**
     * Tests that username is lowercased on save.
     */
    @Test
    void shouldLowercaseUsername() {
        // Arrange
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("ALICE")
                .pubkey("pk")
                .build();

        // Act
        Nip05RecordEntity saved = recordRepository.save(record);

        // Assert
        assertThat(saved.getUsername()).isEqualTo("alice");
    }

    /**
     * Tests that timestamps are set on persist.
     */
    @Test
    void shouldSetTimestampsOnPersist() {
        // Arrange
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("timestamps")
                .pubkey("pk")
                .build();

        // Act
        Nip05RecordEntity saved = recordRepository.save(record);

        // Assert
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
