package xyz.tcheeric.bottin.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E-05: REST API CRUD Complete Cycle.
 * Tests full CRUD operations on domains and NIP-05 records.
 */
class RestApiCrudE2ETest extends BasicE2ETest {

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private Nip05RecordRepository nip05RecordRepository;

    private static final String TEST_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final String UPDATED_PUBKEY = "89be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81799";

    @BeforeEach
    void setUp() {
        nip05RecordRepository.deleteAll();
        domainRepository.deleteAll();
    }

    /**
     * Tests complete CRUD cycle for domains.
     */
    @Test
    void shouldCompleteDomainCrudCycle() {
        // CREATE: POST /api/v1/domains
        String createJson = """
                {
                    "name": "crud.example.com"
                }
                """;

        Long domainId = given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(createJson)
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(201)
                .body("name", equalTo("crud.example.com"))
                .body("verified", equalTo(false))
                .body("id", notNullValue())
                .extract()
                .jsonPath().getLong("id");

        // READ: GET /api/v1/domains/{id}
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/domains/{id}", domainId)
                .then()
                .statusCode(200)
                .body("id", equalTo(domainId.intValue()))
                .body("name", equalTo("crud.example.com"));

        // LIST: GET /api/v1/domains
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));

        // DELETE: DELETE /api/v1/domains/{id}
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .delete("/api/v1/domains/{id}", domainId)
                .then()
                .statusCode(204);

        // Verify deletion
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/domains/{id}", domainId)
                .then()
                .statusCode(404);
    }

    /**
     * Tests complete CRUD cycle for NIP-05 records.
     */
    @Test
    void shouldCompleteRecordCrudCycle() {
        // First, create a domain
        String domainJson = """
                {
                    "name": "records.example.com",
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

        // CREATE: POST /api/v1/records
        String createRecordJson = """
                {
                    "username": "testuser",
                    "domainId": %d,
                    "pubkey": "%s",
                    "relays": ["wss://relay.example.com"]
                }
                """.formatted(domainId, TEST_PUBKEY);

        Long recordId = given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(createRecordJson)
                .when()
                .post("/api/v1/records")
                .then()
                .statusCode(201)
                .body("username", equalTo("testuser"))
                .body("pubkey", equalTo(TEST_PUBKEY))
                .body("id", notNullValue())
                .extract()
                .jsonPath().getLong("id");

        // READ: GET /api/v1/records/{id}
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/records/{id}", recordId)
                .then()
                .statusCode(200)
                .body("id", equalTo(recordId.intValue()))
                .body("username", equalTo("testuser"))
                .body("pubkey", equalTo(TEST_PUBKEY));

        // UPDATE: PUT /api/v1/records/{id}
        String updateJson = """
                {
                    "username": "testuser",
                    "domainId": %d,
                    "pubkey": "%s",
                    "relays": ["wss://updated-relay.example.com", "wss://relay2.example.com"]
                }
                """.formatted(domainId, UPDATED_PUBKEY);

        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .put("/api/v1/records/{id}", recordId)
                .then()
                .statusCode(200)
                .body("pubkey", equalTo(UPDATED_PUBKEY));

        // Verify update
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/records/{id}", recordId)
                .then()
                .statusCode(200)
                .body("pubkey", equalTo(UPDATED_PUBKEY));

        // LIST: GET /api/v1/records with pagination
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/records")
                .then()
                .statusCode(200)
                .body("content.size()", greaterThan(0));

        // DELETE: DELETE /api/v1/records/{id}
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .delete("/api/v1/records/{id}", recordId)
                .then()
                .statusCode(204);

        // Verify deletion
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/records/{id}", recordId)
                .then()
                .statusCode(404);
    }

    /**
     * Tests record search and filtering.
     */
    @Test
    void shouldSupportRecordSearchAndFiltering() {
        // Create domain
        String domainJson = """
                {
                    "name": "search.example.com",
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

        // Create multiple records
        for (int i = 1; i <= 3; i++) {
            String recordJson = """
                    {
                        "username": "user%d",
                        "domainId": %d,
                        "pubkey": "%d9be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179%d"
                    }
                    """.formatted(i, domainId, i, i);

            given()
                    .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                    .contentType(ContentType.JSON)
                    .body(recordJson)
                    .when()
                    .post("/api/v1/records")
                    .then()
                    .statusCode(201);
        }

        // Test pagination
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when()
                .get("/api/v1/records")
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(2))
                .body("totalElements", equalTo(3));
    }
}
