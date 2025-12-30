package xyz.tcheeric.bottin.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * E2E-06: Security and Authentication Tests.
 * Tests authentication requirements and public endpoint access.
 */
class SecurityE2ETest extends BasicE2ETest {

    /**
     * Tests that protected endpoints require authentication.
     */
    @Test
    void shouldRequireAuthenticationForProtectedEndpoints() {
        // API endpoints should return 401 without credentials
        given()
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(401);

        given()
                .when()
                .get("/api/v1/records")
                .then()
                .statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/v1/domains")
                .then()
                .statusCode(401);
    }

    /**
     * Tests that wrong credentials return 401.
     */
    @Test
    void shouldRejectInvalidCredentials() {
        given()
                .auth().basic("wrong", "credentials")
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(401);

        given()
                .auth().basic(ADMIN_USER, "wrong-password")
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(401);
    }

    /**
     * Tests that correct credentials allow access.
     */
    @Test
    void shouldAllowAccessWithValidCredentials() {
        given()
                .auth().basic(ADMIN_USER, ADMIN_PASSWORD)
                .when()
                .get("/api/v1/domains")
                .then()
                .statusCode(200);
    }

    /**
     * Tests that public endpoints work without authentication.
     */
    @Test
    void shouldAllowPublicEndpointsWithoutAuth() {
        // Well-known endpoint should be public
        given()
                .when()
                .get("/.well-known/nostr.json")
                .then()
                .statusCode(200);

        // External verification endpoint should be public
        given()
                .queryParam("nip05", "test@example.com")
                .when()
                .get("/api/v1/verify")
                .then()
                .statusCode(anyOf200or400()); // 200 success or 400 for invalid nip05
    }

    /**
     * Tests CORS headers on well-known endpoint.
     */
    @Test
    void shouldIncludeCorsHeadersOnWellKnownEndpoint() {
        given()
                .header("Origin", "https://example.com")
                .when()
                .get("/.well-known/nostr.json")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", containsString("*"));
    }

    /**
     * Tests that admin UI redirects to login when not authenticated.
     */
    @Test
    void shouldRedirectToLoginForAdminEndpoints() {
        // Admin dashboard should redirect to login
        given()
                .redirects().follow(false)
                .when()
                .get("/admin/dashboard")
                .then()
                .statusCode(302)
                .header("Location", containsString("login"));
    }

    // Helper to match either 200 or 400 status
    private static org.hamcrest.Matcher<Integer> anyOf200or400() {
        return org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.equalTo(200),
                org.hamcrest.Matchers.equalTo(400)
        );
    }
}
