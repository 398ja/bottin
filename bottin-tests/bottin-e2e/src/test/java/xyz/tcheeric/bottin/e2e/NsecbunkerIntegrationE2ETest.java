package xyz.tcheeric.bottin.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.bottin.starter.PersistentNip05Manager;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Record;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E-07: PersistentNip05Manager Integration with nsecbunker-java.
 * Tests the full integration between bottin and nsecbunkerd.
 */
class NsecbunkerIntegrationE2ETest extends BaseE2ETest {

    @Autowired
    private Nip05Manager nip05Manager;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    @BeforeEach
    void setUp() {
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();

        // Create a verified domain for testing
        String domainJson = """
                {
                    "name": "integration.example.com",
                    "verified": true
                }
                """;

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201);
    }

    /**
     * Tests that the injected Nip05Manager is PersistentNip05Manager.
     */
    @Test
    void shouldInjectPersistentNip05Manager() {
        // Assert: Nip05Manager should be PersistentNip05Manager
        assertThat(nip05Manager).isInstanceOf(PersistentNip05Manager.class);
    }

    /**
     * Tests that setupNip05 generates a new keypair and creates a record.
     */
    @Test
    void shouldSetupNip05WithGeneratedKeypair() throws ExecutionException, InterruptedException, TimeoutException {
        // Act: Setup NIP-05
        Nip05Record record = nip05Manager.setupNip05("testuser", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Record should be created with valid pubkey
        assertThat(record).isNotNull();
        assertThat(record.getNip05()).isEqualTo("testuser@integration.example.com");
        assertThat(record.getPubkey()).isNotNull();
        assertThat(record.getPubkey()).hasSize(64); // 64 hex characters
        assertThat(record.getPubkey()).matches("[0-9a-f]{64}");
    }

    /**
     * Tests that verifyNip05 returns true for existing record.
     */
    @Test
    void shouldVerifyExistingNip05() throws ExecutionException, InterruptedException, TimeoutException {
        // Arrange: Create a record
        nip05Manager.setupNip05("verifytest", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Act: Verify the NIP-05
        Boolean verified = nip05Manager.verifyNip05("verifytest@integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Should be verified
        assertThat(verified).isTrue();
    }

    /**
     * Tests that verifyNip05 returns false for non-existent record.
     */
    @Test
    void shouldReturnFalseForNonExistentNip05() throws ExecutionException, InterruptedException, TimeoutException {
        // Act: Verify non-existent NIP-05
        Boolean verified = nip05Manager.verifyNip05("nonexistent@integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Should not be verified
        assertThat(verified).isFalse();
    }

    /**
     * Tests that findByNip05 returns the record.
     */
    @Test
    void shouldFindRecordByNip05() throws ExecutionException, InterruptedException, TimeoutException {
        // Arrange: Create a record
        Nip05Record created = nip05Manager.setupNip05("findtest", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Act: Find by NIP-05
        Optional<Nip05Record> found = nip05Manager.findByNip05("findtest@integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Should find the record
        assertThat(found).isPresent();
        assertThat(found.get().getPubkey()).isEqualTo(created.getPubkey());
    }

    /**
     * Tests that findByDomain returns all records for a domain.
     */
    @Test
    void shouldFindRecordsByDomain() throws ExecutionException, InterruptedException, TimeoutException {
        // Arrange: Create multiple records
        nip05Manager.setupNip05("user1", "integration.example.com")
                .get(10, TimeUnit.SECONDS);
        nip05Manager.setupNip05("user2", "integration.example.com")
                .get(10, TimeUnit.SECONDS);
        nip05Manager.setupNip05("user3", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Act: Find by domain
        List<Nip05Record> records = nip05Manager.findByDomain("integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Should find all records
        assertThat(records).hasSize(3);
        assertThat(records).extracting(Nip05Record::getNip05)
                .containsExactlyInAnyOrder(
                        "user1@integration.example.com",
                        "user2@integration.example.com",
                        "user3@integration.example.com"
                );
    }

    /**
     * Tests that deleteNip05 removes the record.
     */
    @Test
    void shouldDeleteNip05() throws ExecutionException, InterruptedException, TimeoutException {
        // Arrange: Create a record
        nip05Manager.setupNip05("deletetest", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Verify it exists
        Boolean existsBefore = nip05Manager.verifyNip05("deletetest@integration.example.com")
                .get(10, TimeUnit.SECONDS);
        assertThat(existsBefore).isTrue();

        // Act: Delete the record
        nip05Manager.deleteNip05("deletetest@integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Assert: Should no longer exist
        Boolean existsAfter = nip05Manager.verifyNip05("deletetest@integration.example.com")
                .get(10, TimeUnit.SECONDS);
        assertThat(existsAfter).isFalse();
    }

    /**
     * Tests that created record appears in well-known endpoint.
     */
    @Test
    void shouldAppearInWellKnownEndpoint() throws ExecutionException, InterruptedException, TimeoutException {
        // Arrange: Create a record via Nip05Manager
        Nip05Record record = nip05Manager.setupNip05("wellknowntest", "integration.example.com")
                .get(10, TimeUnit.SECONDS);

        // Act & Assert: Should appear in well-known endpoint
        given()
                .when()
                .get("/.well-known/nostr.json?name=wellknowntest")
                .then()
                .statusCode(200)
                .body("names.wellknowntest", equalTo(record.getPubkey()));
    }

    /**
     * Tests the nsecbunkerd container is running and accessible.
     */
    @Test
    void shouldHaveRunningNsecbunkerd() {
        // Assert: Container should be running
        assertThat(nsecbunkerdContainer.isRunning()).isTrue();

        String nsecbunkerdUrl = getNsecbunkerdUrl();
        assertThat(nsecbunkerdUrl).isNotEmpty();
    }

    /**
     * Tests the strfry relay container is running and accessible.
     */
    @Test
    void shouldHaveRunningStrfryRelay() {
        // Assert: Container should be running
        assertThat(strfryContainer.isRunning()).isTrue();

        String relayUrl = getRelayUrl();
        assertThat(relayUrl).startsWith("ws://");
    }
}
