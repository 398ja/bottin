package xyz.tcheeric.bottin.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.bottin.service.WellKnownJsonGenerator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-03: Well-Known Service Integration Tests.
 * Verifies NIP-05 JSON generation with real database data.
 */
class WellKnownServiceIT extends BaseIntegrationTest {

    @Autowired
    private WellKnownJsonGenerator wellKnownJsonGenerator;

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    @Autowired
    private DomainRepository domainRepository;

    private DomainEntity testDomain;

    private static final String ALICE_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final String BOB_PUBKEY = "89be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81799";
    private static final String CHARLIE_PUBKEY = "99be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81800";

    @BeforeEach
    void setUp() {
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();

        testDomain = domainRepository.save(
                DomainEntity.builder()
                        .name("example.com")
                        .verified(true)
                        .build()
        );
    }

    /**
     * Tests that multiple NIP-05 records for the same domain generate correct JSON structure.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateCorrectJsonForMultipleRecords() {
        // Arrange: Create multiple records for the domain
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("alice")
                        .pubkey(ALICE_PUBKEY)
                        .relaysJson("[\"wss://relay1.example.com\"]")
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("bob")
                        .pubkey(BOB_PUBKEY)
                        .relaysJson("[\"wss://relay2.example.com\",\"wss://relay3.example.com\"]")
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("charlie")
                        .pubkey(CHARLIE_PUBKEY)
                        .relaysJson("[]")
                        .enabled(true)
                        .build()
        );

        // Act: Generate JSON for the domain
        Map<String, Object> response = wellKnownJsonGenerator.generateForDomain("example.com");

        // Assert: Verify the names object contains all usernames mapped to pubkeys
        assertThat(response).containsKey("names");
        Map<String, String> names = (Map<String, String>) response.get("names");
        assertThat(names).hasSize(3);
        assertThat(names.get("alice")).isEqualTo(ALICE_PUBKEY);
        assertThat(names.get("bob")).isEqualTo(BOB_PUBKEY);
        assertThat(names.get("charlie")).isEqualTo(CHARLIE_PUBKEY);
    }

    /**
     * Tests that the relays object contains relay lists for each pubkey.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeRelaysForEachPubkey() {
        // Arrange: Create records with relays
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("alice")
                        .pubkey(ALICE_PUBKEY)
                        .relaysJson("[\"wss://relay1.example.com\",\"wss://relay2.example.com\"]")
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("bob")
                        .pubkey(BOB_PUBKEY)
                        .relaysJson("[\"wss://bob-relay.example.com\"]")
                        .enabled(true)
                        .build()
        );

        // Act: Generate JSON
        Map<String, Object> response = wellKnownJsonGenerator.generateForDomain("example.com");

        // Assert: Verify relays object
        assertThat(response).containsKey("relays");
        Map<String, List<String>> relays = (Map<String, List<String>>) response.get("relays");
        assertThat(relays).hasSize(2);
        assertThat(relays.get(ALICE_PUBKEY))
                .containsExactly("wss://relay1.example.com", "wss://relay2.example.com");
        assertThat(relays.get(BOB_PUBKEY))
                .containsExactly("wss://bob-relay.example.com");
    }

    /**
     * Tests filtering by specific username returns only that record.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterBySpecificUsername() {
        // Arrange: Create multiple records
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("alice")
                        .pubkey(ALICE_PUBKEY)
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("bob")
                        .pubkey(BOB_PUBKEY)
                        .enabled(true)
                        .build()
        );

        // Act: Generate JSON for specific username
        Optional<Map<String, Object>> response = wellKnownJsonGenerator
                .generateForUsername("example.com", "alice");

        // Assert: Only alice's record should be returned
        assertThat(response).isPresent();
        Map<String, String> names = (Map<String, String>) response.get().get("names");
        assertThat(names).hasSize(1);
        assertThat(names.get("alice")).isEqualTo(ALICE_PUBKEY);
        assertThat(names).doesNotContainKey("bob");
    }

    /**
     * Tests that disabled records are excluded from the response.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeDisabledRecords() {
        // Arrange: Create enabled and disabled records
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("enabled")
                        .pubkey(ALICE_PUBKEY)
                        .enabled(true)
                        .build()
        );
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("disabled")
                        .pubkey(BOB_PUBKEY)
                        .enabled(false)
                        .build()
        );

        // Act: Generate JSON for domain
        Map<String, Object> response = wellKnownJsonGenerator.generateForDomain("example.com");

        // Assert: Only enabled record should be included
        Map<String, String> names = (Map<String, String>) response.get("names");
        assertThat(names).hasSize(1);
        assertThat(names).containsKey("enabled");
        assertThat(names).doesNotContainKey("disabled");
    }

    /**
     * Tests that requesting a disabled user returns empty.
     */
    @Test
    void shouldReturnEmptyForDisabledUser() {
        // Arrange: Create a disabled record
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("disabled")
                        .pubkey(ALICE_PUBKEY)
                        .enabled(false)
                        .build()
        );

        // Act: Request the disabled user
        Optional<Map<String, Object>> response = wellKnownJsonGenerator
                .generateForUsername("example.com", "disabled");

        // Assert: Should return empty
        assertThat(response).isEmpty();
    }

    /**
     * Tests that requesting a non-existent username returns empty.
     */
    @Test
    void shouldReturnEmptyForNonExistentUsername() {
        // Arrange: Create a record
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("exists")
                        .pubkey(ALICE_PUBKEY)
                        .enabled(true)
                        .build()
        );

        // Act: Request a non-existent user
        Optional<Map<String, Object>> response = wellKnownJsonGenerator
                .generateForUsername("example.com", "nonexistent");

        // Assert: Should return empty
        assertThat(response).isEmpty();
    }

    /**
     * Tests that requesting a non-existent domain returns empty names.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmptyNamesForNonExistentDomain() {
        // Act: Request a non-existent domain
        Map<String, Object> response = wellKnownJsonGenerator.generateForDomain("nonexistent.com");

        // Assert: Should return empty names
        assertThat(response).containsKey("names");
        Map<String, String> names = (Map<String, String>) response.get("names");
        assertThat(names).isEmpty();
    }

    /**
     * Tests that records without relays don't include a relays entry.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldNotIncludeRelaysEntryWhenNoRelays() {
        // Arrange: Create record without relays
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("norelays")
                        .pubkey(ALICE_PUBKEY)
                        .relaysJson("[]")
                        .enabled(true)
                        .build()
        );

        // Act: Generate JSON
        Map<String, Object> response = wellKnownJsonGenerator.generateForDomain("example.com");

        // Assert: Names should exist but relays should not
        assertThat(response).containsKey("names");
        Map<String, String> names = (Map<String, String>) response.get("names");
        assertThat(names.get("norelays")).isEqualTo(ALICE_PUBKEY);

        // Relays should not be present when empty
        assertThat(response).doesNotContainKey("relays");
    }

    /**
     * Tests case-insensitive username lookup.
     */
    @Test
    void shouldHandleCaseInsensitiveUsernameLookup() {
        // Arrange: Create record with lowercase username
        nip05RecordRepository.save(
                Nip05RecordEntity.builder()
                        .domain(testDomain)
                        .username("alice")
                        .pubkey(ALICE_PUBKEY)
                        .enabled(true)
                        .build()
        );

        // Act: Request with uppercase
        Optional<Map<String, Object>> response = wellKnownJsonGenerator
                .generateForUsername("example.com", "ALICE");

        // Assert: Should find the record
        assertThat(response).isPresent();
    }
}
