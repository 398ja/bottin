package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WellKnownJsonGenerator.
 * Tests the NIP-05 JSON generation logic.
 */
@ExtendWith(MockitoExtension.class)
class WellKnownJsonGeneratorTest {

    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_USERNAME = "alice";
    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Mock
    private Nip05RecordRepository nip05RecordRepository;

    @Mock
    private DomainRepository domainRepository;

    private WellKnownJsonGenerator generator;
    private DomainEntity testDomainEntity;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        generator = new WellKnownJsonGenerator(nip05RecordRepository, domainRepository, objectMapper);

        testDomainEntity = DomainEntity.builder()
                .id(1L)
                .name(TEST_DOMAIN)
                .verified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Tests that generateForUsername returns the correct record for a valid username.
     */
    @Test
    void shouldReturnRecordWhenUsernameExists() {
        // Arrange: Create test record
        Nip05RecordEntity record = createTestRecord(TEST_USERNAME, TEST_PUBKEY, "[]");
        when(nip05RecordRepository.findByUsernameAndDomainName(TEST_USERNAME, TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Generate JSON for username
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, TEST_USERNAME);

        // Assert: Verify response structure
        assertThat(result).isPresent();
        Map<String, Object> response = result.get();
        assertThat(response).containsKey("names");
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) response.get("names");
        assertThat(names).containsEntry(TEST_USERNAME, TEST_PUBKEY);
    }

    /**
     * Tests that generateForUsername returns empty when username doesn't exist.
     */
    @Test
    void shouldReturnEmptyWhenUsernameNotFound() {
        // Arrange: Mock repository to return empty
        when(nip05RecordRepository.findByUsernameAndDomainName(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Act: Generate JSON for non-existent username
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, "nonexistent");

        // Assert: Verify empty response
        assertThat(result).isEmpty();
    }

    /**
     * Tests that generateForUsername returns empty when record is disabled.
     */
    @Test
    void shouldReturnEmptyWhenRecordDisabled() {
        // Arrange: Create disabled record
        Nip05RecordEntity record = createTestRecord(TEST_USERNAME, TEST_PUBKEY, "[]");
        record.setEnabled(false);
        when(nip05RecordRepository.findByUsernameAndDomainName(TEST_USERNAME, TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Generate JSON for disabled user
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, TEST_USERNAME);

        // Assert: Verify empty response
        assertThat(result).isEmpty();
    }

    /**
     * Tests that relays are included when the record has relay information.
     */
    @Test
    void shouldIncludeRelaysWhenPresent() {
        // Arrange: Create record with relays
        String relaysJson = "[\"wss://relay1.example.com\", \"wss://relay2.example.com\"]";
        Nip05RecordEntity record = createTestRecord(TEST_USERNAME, TEST_PUBKEY, relaysJson);
        when(nip05RecordRepository.findByUsernameAndDomainName(TEST_USERNAME, TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Generate JSON for username
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, TEST_USERNAME);

        // Assert: Verify relays are included
        assertThat(result).isPresent();
        Map<String, Object> response = result.get();
        assertThat(response).containsKey("relays");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> relays = (Map<String, List<String>>) response.get("relays");
        assertThat(relays).containsKey(TEST_PUBKEY);
        assertThat(relays.get(TEST_PUBKEY)).containsExactly(
                "wss://relay1.example.com",
                "wss://relay2.example.com"
        );
    }

    /**
     * Tests that relays are omitted when the record has no relay information.
     */
    @Test
    void shouldOmitRelaysWhenEmpty() {
        // Arrange: Create record without relays
        Nip05RecordEntity record = createTestRecord(TEST_USERNAME, TEST_PUBKEY, "[]");
        when(nip05RecordRepository.findByUsernameAndDomainName(TEST_USERNAME, TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Generate JSON for username
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, TEST_USERNAME);

        // Assert: Verify relays are omitted
        assertThat(result).isPresent();
        Map<String, Object> response = result.get();
        assertThat(response).doesNotContainKey("relays");
    }

    /**
     * Tests that generateForDomain returns all enabled records for a domain.
     */
    @Test
    void shouldReturnAllEnabledRecordsForDomain() {
        // Arrange: Create multiple records
        Nip05RecordEntity alice = createTestRecord("alice", TEST_PUBKEY, "[]");
        Nip05RecordEntity bob = createTestRecord("bob",
                "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", "[]");

        when(domainRepository.existsByName(TEST_DOMAIN)).thenReturn(true);
        when(nip05RecordRepository.findByDomainNameAndEnabledTrue(TEST_DOMAIN))
                .thenReturn(List.of(alice, bob));

        // Act: Generate JSON for domain
        Map<String, Object> result = generator.generateForDomain(TEST_DOMAIN);

        // Assert: Verify both records are included
        assertThat(result).containsKey("names");
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) result.get("names");
        assertThat(names).hasSize(2);
        assertThat(names).containsKey("alice");
        assertThat(names).containsKey("bob");
    }

    /**
     * Tests that generateForDomain returns empty names for non-existent domain.
     */
    @Test
    void shouldReturnEmptyNamesForNonExistentDomain() {
        // Arrange: Mock domain not found
        when(domainRepository.existsByName("nonexistent.com")).thenReturn(false);

        // Act: Generate JSON for non-existent domain
        Map<String, Object> result = generator.generateForDomain("nonexistent.com");

        // Assert: Verify empty names
        assertThat(result).containsKey("names");
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) result.get("names");
        assertThat(names).isEmpty();
    }

    /**
     * Tests that username lookup is case-insensitive.
     */
    @Test
    void shouldHandleCaseInsensitiveUsernameLookup() {
        // Arrange: Create record with lowercase username
        Nip05RecordEntity record = createTestRecord("alice", TEST_PUBKEY, "[]");
        when(nip05RecordRepository.findByUsernameAndDomainName("alice", TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Look up with uppercase (generator normalizes to lowercase)
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, "ALICE");

        // Assert: Verify record is found
        assertThat(result).isPresent();
    }

    /**
     * Tests that invalid relay JSON is handled gracefully.
     */
    @Test
    void shouldHandleInvalidRelayJsonGracefully() {
        // Arrange: Create record with invalid relays JSON
        Nip05RecordEntity record = createTestRecord(TEST_USERNAME, TEST_PUBKEY, "invalid-json");
        when(nip05RecordRepository.findByUsernameAndDomainName(TEST_USERNAME, TEST_DOMAIN))
                .thenReturn(Optional.of(record));

        // Act: Generate JSON for username
        Optional<Map<String, Object>> result = generator.generateForUsername(TEST_DOMAIN, TEST_USERNAME);

        // Assert: Verify response is valid but without relays
        assertThat(result).isPresent();
        Map<String, Object> response = result.get();
        assertThat(response).containsKey("names");
        assertThat(response).doesNotContainKey("relays");
    }

    private Nip05RecordEntity createTestRecord(String username, String pubkey, String relaysJson) {
        return Nip05RecordEntity.builder()
                .id(1L)
                .domain(testDomainEntity)
                .username(username)
                .pubkey(pubkey)
                .relaysJson(relaysJson)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
