package xyz.tcheeric.bottin.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E-08: Error Handling and Edge Cases.
 * Tests proper error responses for invalid inputs and edge cases.
 */
class ErrorHandlingE2ETest extends BasicE2ETest {

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    private Long testDomainId;

    @BeforeEach
    void setUp() {
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();

        // Create a test domain
        String domainJson = """
                {
                    "name": "error.example.com",
                    "verified": true
                }
                """;

        Integer id = given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        testDomainId = id.longValue();
    }

    /**
     * Tests that invalid pubkey format returns 400 with clear error.
     */
    @Test
    void shouldReturn400ForInvalidPubkeyFormat() {
        String invalidPubkeyJson = """
                {
                    "username": "testuser",
                    "domainId": %d,
                    "pubkey": "invalid-not-64-hex-chars"
                }
                """.formatted(testDomainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(invalidPubkeyJson)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    /**
     * Tests that non-existent domain returns 404.
     */
    @Test
    void shouldReturn404ForNonExistentDomain() {
        String recordWithBadDomain = """
                {
                    "username": "testuser",
                    "domainId": 99999,
                    "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                }
                """;

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(recordWithBadDomain)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(404);
    }

    /**
     * Tests that duplicate record returns 409 Conflict.
     */
    @Test
    void shouldReturn409ForDuplicateRecord() {
        String recordJson = """
                {
                    "username": "duplicate",
                    "domainId": %d,
                    "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                }
                """.formatted(testDomainId);

        // First creation should succeed
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(recordJson)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(201);

        // Second creation should fail with 409
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(recordJson)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(409);
    }

    /**
     * Tests that non-existent record returns 404.
     */
    @Test
    void shouldReturn404ForNonExistentRecord() {
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/records/{id}", 99999)
                .then()
                .statusCode(404);
    }

    /**
     * Tests that malformed JSON returns 400 Bad Request.
     */
    @Test
    void shouldReturn400ForMalformedJson() {
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body("{ invalid json }")
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(400);
    }

    /**
     * Tests that missing required fields return 400.
     */
    @Test
    void shouldReturn400ForMissingRequiredFields() {
        // Missing username
        String missingUsername = """
                {
                    "domainId": %d,
                    "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                }
                """.formatted(testDomainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(missingUsername)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(400);

        // Missing pubkey
        String missingPubkey = """
                {
                    "username": "test",
                    "domainId": %d
                }
                """.formatted(testDomainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(missingPubkey)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(400);
    }

    /**
     * Tests that empty body returns 400.
     */
    @Test
    void shouldReturn400ForEmptyBody() {
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(400);
    }

    /**
     * Tests that well-known with non-existent username returns empty names.
     */
    @Test
    void shouldReturnEmptyNamesForNonExistentUser() {
        given()
                .when()
                .get("/.well-known/nostr.json?name=nonexistent")
                .then()
                .statusCode(200)
                .body("names.size()", equalTo(0));
    }

    /**
     * Tests that duplicate domain name returns 409.
     */
    @Test
    void shouldReturn409ForDuplicateDomain() {
        String domainJson = """
                {
                    "name": "duplicate.example.com"
                }
                """;

        // First creation should succeed
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201);

        // Second creation should fail with 409
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(domainJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(409);
    }

    /**
     * Tests that PUT on non-existent record returns 404.
     */
    @Test
    void shouldReturn404WhenUpdatingNonExistentRecord() {
        String updateJson = """
                {
                    "username": "test",
                    "domainId": %d,
                    "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
                }
                """.formatted(testDomainId);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .put("/api/v1/records/{id}", 99999)
                .then()
                .statusCode(404);
    }
}
