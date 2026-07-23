package xyz.tcheeric.bottin.client.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ApiEndpointIT extends BaseClientIT {

    private static final String VALID_PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void shouldResolveAvailableUsername() {
        var response = rest.getForEntity("/api/v1/resolve/alice", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"available\":true");
    }

    @Test
    void shouldRejectInvalidUsername() {
        var response = rest.getForEntity("/api/v1/resolve/ALICE", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"available\":false");
    }

    @Test
    void shouldReturnEmptySearchForBlankQuery() {
        var response = rest.getForEntity("/api/v1/search?q=", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"total\":0");
    }

    @Test
    void shouldSearchWithQueryAndReturnEmpty() {
        var response = rest.getForEntity("/api/v1/search?q=nonexistent", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"total\":0");
    }

    @Test
    void shouldListFollows() {
        var response = rest.getForEntity("/api/v1/follows", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"follows\":[]");
    }

    @Test
    void shouldListBlocks() {
        var response = rest.getForEntity("/api/v1/blocks", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"blocks\":[]");
    }

    @Test
    void shouldListRelays() {
        var response = rest.getForEntity("/api/v1/relays", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"relays\":[]");
    }

    @Test
    void shouldFollowValidPubkey() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var body = "{\"pubkey\":\"" + VALID_PUBKEY + "\"}";
        var request = new org.springframework.http.HttpEntity<>(body, headers);
        var response = rest.postForEntity("/api/v1/follow", request, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"followed\"");
    }

    @Test
    void shouldBlockValidPubkey() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var body = "{\"pubkey\":\"" + VALID_PUBKEY + "\"}";
        var request = new org.springframework.http.HttpEntity<>(body, headers);
        var response = rest.postForEntity("/api/v1/block", request, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"blocked\"");
    }
}
