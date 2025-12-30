package xyz.tcheeric.bottin.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-01: NIP-05 Record Repository Integration Tests.
 * Verifies CRUD operations work correctly with PostgreSQL database.
 */
@Transactional
class Nip05RecordRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    @Autowired
    private DomainRepository domainRepository;

    private DomainEntity testDomain;

    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final String TEST_PUBKEY_2 = "89be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81799";

    @BeforeEach
    void setUp() {
        // Clean up before each test
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();

        // Create a test domain
        testDomain = domainRepository.save(
                DomainEntity.builder()
                        .name("example.com")
                        .verified(true)
                        .build()
        );
    }

    /**
     * Tests that a NIP-05 record persists correctly in the database.
     * Creates a record and verifies all fields are saved.
     */
    @Test
    void shouldPersistNip05RecordCorrectly() {
        // Arrange: Create a NIP-05 record
        Nip05RecordEntity record = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("alice")
                .pubkey(TEST_PUBKEY)
                .relaysJson("[\"wss://relay1.example.com\"]")
                .enabled(true)
                .build();

        // Act: Save the record
        Nip05RecordEntity saved = nip05RecordRepository.save(record);

        // Assert: Verify the record was saved with correct values
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPubkey()).isEqualTo(TEST_PUBKEY);
        assertThat(saved.getRelaysJson()).isEqualTo("[\"wss://relay1.example.com\"]");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getNip05()).isEqualTo("alice@example.com");
    }

    /**
     * Tests querying records by username and domain returns the correct record.
     */
    @Test
    void shouldFindRecordByUsernameAndDomain() {
        // Arrange: Create and save a record
        Nip05RecordEntity record = nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("bob")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );

        // Act: Query by username and domain
        Optional<Nip05RecordEntity> found = nip05RecordRepository
                .findByUsernameAndDomain("bob", testDomain);

        // Assert: Verify the correct record is returned
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(record.getId());
        assertThat(found.get().getUsername()).isEqualTo("bob");
    }

    /**
     * Tests querying records by username and domain name returns the correct record.
     */
    @Test
    void shouldFindRecordByUsernameAndDomainName() {
        // Arrange: Create and save a record
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("charlie")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );

        // Act: Query by username and domain name
        Optional<Nip05RecordEntity> found = nip05RecordRepository
                .findByUsernameAndDomainName("charlie", "example.com");

        // Assert: Verify the correct record is returned
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("charlie");
        assertThat(found.get().getDomain().getName()).isEqualTo("example.com");
    }

    /**
     * Tests updating a record's pubkey and relays persists the changes.
     */
    @Test
    void shouldUpdateRecordPubkeyAndRelays() {
        // Arrange: Create and save a record
        Nip05RecordEntity record = nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("dave")
                        .pubkey(TEST_PUBKEY)
                        .relaysJson("[]")
                        .build()
        );

        // Act: Update pubkey and relays
        record.setPubkey(TEST_PUBKEY_2);
        record.setRelaysJson("[\"wss://updated-relay.example.com\"]");
        Nip05RecordEntity updated = nip05RecordRepository.save(record);

        // Assert: Verify changes are saved
        assertThat(updated.getPubkey()).isEqualTo(TEST_PUBKEY_2);
        assertThat(updated.getRelaysJson()).isEqualTo("[\"wss://updated-relay.example.com\"]");

        // Verify by re-fetching from database
        Optional<Nip05RecordEntity> refetched = nip05RecordRepository.findById(record.getId());
        assertThat(refetched).isPresent();
        assertThat(refetched.get().getPubkey()).isEqualTo(TEST_PUBKEY_2);
    }

    /**
     * Tests deleting a record removes it from the database.
     */
    @Test
    void shouldDeleteRecordFromDatabase() {
        // Arrange: Create and save a record
        Nip05RecordEntity record = nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("eve")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );
        Long recordId = record.getId();

        // Act: Delete the record
        nip05RecordRepository.delete(record);

        // Assert: Verify the record no longer exists
        Optional<Nip05RecordEntity> deleted = nip05RecordRepository.findById(recordId);
        assertThat(deleted).isEmpty();
    }

    /**
     * Tests that creating a duplicate username@domain fails with constraint violation.
     */
    @Test
    void shouldFailWhenCreatingDuplicateUsernameAtDomain() {
        // Arrange: Create and save first record
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("frank")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );

        // Act & Assert: Creating duplicate should fail
        Nip05RecordEntity duplicate = Nip05RecordEntity.builder()
                .domain(testDomain)
                .username("frank")
                .pubkey(TEST_PUBKEY_2)
                .build();

        assertThatThrownBy(() -> {
            nip05RecordRepository.save(duplicate);
            nip05RecordRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Tests that usernames are normalized to lowercase.
     */
    @Test
    void shouldNormalizeUsernameToLowercase() {
        // Arrange & Act: Create a record with uppercase username
        Nip05RecordEntity record = nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("ALICE")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );

        // Assert: Username should be lowercase
        assertThat(record.getUsername()).isEqualTo("alice");
    }

    /**
     * Tests finding records by pubkey.
     */
    @Test
    void shouldFindRecordByPubkey() {
        // Arrange: Create and save a record
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("grace")
                        .pubkey(TEST_PUBKEY)
                        .build()
        );

        // Act: Find by pubkey
        Optional<Nip05RecordEntity> found = nip05RecordRepository.findByPubkey(TEST_PUBKEY);

        // Assert: Verify the record is found
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("grace");
    }

    /**
     * Tests checking existence of enabled records.
     */
    @Test
    void shouldCheckExistenceOfEnabledRecords() {
        // Arrange: Create enabled and disabled records
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("enabled")
                        .pubkey(TEST_PUBKEY)
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("disabled")
                        .pubkey(TEST_PUBKEY_2)
                        .enabled(false)
                        .build()
        );

        // Act & Assert: Check existence
        assertThat(nip05RecordRepository.existsByUsernameAndDomainNameAndEnabledTrue("enabled", "example.com"))
                .isTrue();
        assertThat(nip05RecordRepository.existsByUsernameAndDomainNameAndEnabledTrue("disabled", "example.com"))
                .isFalse();
        assertThat(nip05RecordRepository.existsByUsernameAndDomainNameAndEnabledTrue("nonexistent", "example.com"))
                .isFalse();
    }
}
