package xyz.tcheeric.bottin.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E-01: Complete NIP-05 Registration Flow.
 * Tests the full flow from domain registration to NIP-05 lookup.
 */
class Nip05RegistrationFlowE2ETest extends BasicE2ETest {

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
     * Tests the complete NIP-05 registration flow:
     * 1. Register a new domain
     * 2. Verify domain appears in list
     * 3. Create a NIP-05 record
     * 4. Query well-known endpoint
     * 5. Verify response contains correct pubkey and relays
     */
    @Test
    void shouldCompleteNip05RegistrationFlow() {
        // Step 1: Register a new domain
        String domainJson = """
                {
                    "name": "test.example.com",
                    "verified": true
                }
                """;

        Long domainId = given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201)
                .body("name", equalTo("test.example.com"))
                .body("id", notNullValue())
                .extract()
                .jsonPath().getLong("id");

        // Step 2: Verify domain appears in domains list
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("test.example.com"));

        // Step 3: Create a NIP-05 record
        String recordJson = """
                {
                    "username": "alice",
                    "domainId": %d,
                    "pubkey": "%s",
                    "relays": ["wss://relay1.example.com", "wss://relay2.example.com"]
                }
                """.formatted(domainId, TEST_PUBKEY);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(recordJson)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(201)
                .body("username", equalTo("alice"))
                .body("pubkey", equalTo(TEST_PUBKEY))
                .body("id", notNullValue());

        // Step 4 & 5: Query well-known endpoint and verify response
        given()
                .when()
                .get("/.well-known/nostr.json?name=alice")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("names.alice", equalTo(TEST_PUBKEY))
                .body("relays", hasKey(TEST_PUBKEY));
    }

    /**
     * Tests that well-known endpoint returns all records when no name specified.
     */
    @Test
    void shouldReturnAllRecordsWhenNoNameSpecified() {
        // Create domain and two records
        String domainJson = """
                {
                    "name": "multi.example.com",
                    "verified": true
                }
                """;

        Long domainId = given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath().getLong("id");

        // Create first record
        String record1Json = """
                {
                    "username": "user1",
                    "domainId": %d,
                    "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                }
                """.formatted(domainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(record1Json)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(201);

        // Create second record
        String record2Json = """
                {
                    "username": "user2",
                    "domainId": %d,
                    "pubkey": "89be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81799"
                }
                """.formatted(domainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(record2Json)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(201);

        // Query well-known without name parameter
        given()
                .when()
                .get("/.well-known/nostr.json")
                .then()
                .statusCode(200)
                .body("names", hasKey("user1"))
                .body("names", hasKey("user2"));
    }

    /**
     * Tests that well-known returns empty names for non-existent username.
     */
    @Test
    void shouldReturnEmptyNamesForNonExistentUsername() {
        // Create domain
        String domainJson = """
                {
                    "name": "empty.example.com",
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

        // Query for non-existent user
        given()
                .when()
                .get("/.well-known/nostr.json?name=nonexistent")
                .then()
                .statusCode(200)
                .body("names.size()", equalTo(0));
    }
}
