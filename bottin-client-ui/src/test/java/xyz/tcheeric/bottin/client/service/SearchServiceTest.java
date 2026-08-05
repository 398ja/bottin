package xyz.tcheeric.bottin.client.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import xyz.tcheeric.bottin.client.dto.SearchResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link SearchService}.
 *
 * <p>The response bodies here are written to the full shape the directory
 * actually serves — every field of {@code PageResponse} and of
 * {@code Nip05RecordResponse} — rather than to the narrower record this client
 * deserialises into. Trimming them to the fields the client reads would make
 * the test agree with the client's assumption instead of checking it.
 */
class SearchServiceTest {

    private static final String DIRECTORY_URL = "http://directory.test";

    private static final String ALICE_HEX =
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";
    private static final String RETIRED_HEX =
            "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2";

    private MockRestServiceServer directory;
    private SearchService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(DIRECTORY_URL);
        directory = MockRestServiceServer.bindTo(builder).build();
        service = new SearchService(builder);
    }

    /**
     * Tests that a live record becomes a search result carrying the key the
     * domain vouches for and the full identifier a reader has to check.
     */
    @Test
    void shouldReturnTheRegisteredHandlesMatchingTheQuery() {
        // Given: the directory holds one live record for "alice"
        directory.expect(requestTo(DIRECTORY_URL + "/api/v1/records?username=alice&size=20"))
                .andRespond(withSuccess(page(record(1, "alice", ALICE_HEX, true)),
                        MediaType.APPLICATION_JSON));

        // When: searching for it
        List<SearchResult> results = service.search("alice", 20);

        // Then: the handle, the key and the identifier all arrive
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("alice");
        assertThat(results.getFirst().getPubkey()).isEqualTo(ALICE_HEX);
        assertThat(results.getFirst().getNip05()).isEqualTo("alice@example.test");
        directory.verify();
    }

    /**
     * Tests that a record the operator has switched off is not offered. The
     * assertion is on the returned list rather than on the request, because the
     * directory returns disabled records either way and the filtering is this
     * client's job.
     */
    @Test
    void shouldOmitDisabledRecordsFromResults() {
        // Given: the directory returns one live and one disabled match
        directory.expect(requestTo(DIRECTORY_URL + "/api/v1/records?username=al&size=20"))
                .andRespond(withSuccess(page(
                                record(1, "alice", ALICE_HEX, true),
                                record(2, "alfred", RETIRED_HEX, false)),
                        MediaType.APPLICATION_JSON));

        // When: searching
        List<SearchResult> results = service.search("al", 20);

        // Then: only the record /.well-known would still serve is offered
        assertThat(results).extracting(SearchResult::getName).containsExactly("alice");
        directory.verify();
    }

    /**
     * Tests that an unreachable directory raises instead of returning nothing.
     * An empty list would reach the searcher as "no such person", which is a
     * claim about the directory's contents that nothing here has established.
     */
    @Test
    void shouldRaiseRatherThanReportNoMatchesWhenTheDirectoryFails() {
        // Given: the directory is failing
        directory.expect(requestTo(DIRECTORY_URL + "/api/v1/records?username=alice&size=20"))
                .andRespond(withServerError());

        // When: searching  // Then: it raises rather than answering empty
        assertThatThrownBy(() -> service.search("alice", 20))
                .isInstanceOf(DirectorySearchException.class)
                .hasMessageContaining("alice");
        directory.verify();
    }

    /**
     * Tests that a blank query never reaches the directory. The mock server has
     * no expectation registered, so any call at all fails the test.
     */
    @Test
    void shouldNotCallTheDirectoryForABlankQuery() {
        // When: searching for whitespace
        List<SearchResult> results = service.search("   ", 20);

        // Then: nothing was asked and nothing was found
        assertThat(results).isEmpty();
        directory.verify();
    }

    /**
     * Tests that a query longer than the directory should ever be asked is
     * refused here rather than forwarded.
     */
    @Test
    void shouldNotCallTheDirectoryForAnOverlongQuery() {
        // Given: a query past the 1000 character ceiling
        String overlong = "a".repeat(1001);

        // When: searching with it
        List<SearchResult> results = service.search(overlong, 20);

        // Then: nothing was asked and nothing was found
        assertThat(results).isEmpty();
        directory.verify();
    }

    /**
     * Tests that the caller's limit reaches the directory as the page size, so
     * a request for five does not silently fetch the default twenty.
     */
    @Test
    void shouldAskTheDirectoryForTheRequestedNumberOfRecords() {
        // Given: the directory expects a page of five
        directory.expect(requestTo(DIRECTORY_URL + "/api/v1/records?username=alice&size=5"))
                .andRespond(withSuccess(page(), MediaType.APPLICATION_JSON));

        // When: searching with that limit
        service.search("alice", 5);

        // Then: the expected request was the one made
        directory.verify();
    }

    private static String page(String... records) {
        return """
                {"content":[%s],
                 "page":0,"size":20,"totalElements":%d,"totalPages":1,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false}"""
                .formatted(String.join(",", records), records.length);
    }

    private static String record(long id, String username, String pubkey, boolean enabled) {
        return """
                {"id":%d,"nip05":"%s@example.test","username":"%s","domain":"example.test",
                 "pubkey":"%s","relays":["wss://relay.example"],"enabled":%b,
                 "createdAt":"2026-08-01T12:00:00Z","updatedAt":"2026-08-01T12:00:00Z"}"""
                .formatted(id, username, username, pubkey, enabled);
    }
}
