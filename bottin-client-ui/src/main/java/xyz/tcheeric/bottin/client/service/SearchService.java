package xyz.tcheeric.bottin.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import xyz.tcheeric.bottin.client.config.ClientProperties;
import xyz.tcheeric.bottin.client.dto.DirectoryRecordPage;
import xyz.tcheeric.bottin.client.dto.SearchResult;

import java.util.List;

/**
 * Finds the handles registered in this deployment's directory.
 *
 * <p>Searches the directory's own records rather than profiles harvested from
 * relays. A relay's {@code nip05} field is a claim made by the key holder;
 * these records are what {@code /.well-known/nostr.json} answers with, so they
 * are the statement that claim is checked against. For this domain, reading
 * them is the verification.
 *
 * <p>The browser never makes this call: the directory URL is an internal
 * address it cannot resolve, and the credentials must not leave this server.
 */
@Service
@Slf4j
public class SearchService {

    /**
     * Longest query passed to the directory. Far beyond any handle, so it
     * refuses only the pathological.
     */
    private static final int MAX_QUERY_LENGTH = 1000;

    private final RestClient restClient;

    @Autowired
    public SearchService(ClientProperties clientProperties) {
        this(RestClient.builder()
                .baseUrl(clientProperties.getDirectoryUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        clientProperties.getDirectoryUsername(),
                        clientProperties.getDirectoryPassword())));
    }

    SearchService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * The live handles whose username contains {@code query}, case-insensitively.
     *
     * <p>Disabled records are dropped: the directory returns them so an operator
     * can see what they have switched off, but {@code /.well-known/nostr.json}
     * no longer serves them, so offering one to a searcher would name an
     * identifier that will not verify.
     *
     * @throws DirectorySearchException when the directory could not be reached
     */
    public List<SearchResult> search(String query, int limit) {
        String username = query == null ? "" : query.trim();
        if (username.isEmpty() || username.length() > MAX_QUERY_LENGTH) {
            return List.of();
        }

        return fetchPage(username, limit).content().stream()
                .filter(DirectoryRecordPage.DirectoryRecord::enabled)
                .map(SearchService::toSearchResult)
                .toList();
    }

    private DirectoryRecordPage fetchPage(String username, int limit) {
        try {
            DirectoryRecordPage page = restClient.get()
                    .uri(uri -> uri.path("/api/v1/records")
                            .queryParam("username", username)
                            .queryParam("size", limit)
                            .build())
                    .retrieve()
                    .body(DirectoryRecordPage.class);
            return page == null ? DirectoryRecordPage.empty() : page;
        } catch (RestClientException e) {
            log.warn("profile_search_failed username={} reason=directory_unreachable error={}",
                    username, e.getMessage());
            throw new DirectorySearchException(
                    "Could not reach the bottin directory to search for " + username, e);
        }
    }

    /**
     * Carries only what the directory knows. Display name, picture and follow
     * state are left unset: they live on relays and in the signed-in user's own
     * data, neither of which this lookup consults.
     */
    private static SearchResult toSearchResult(DirectoryRecordPage.DirectoryRecord record) {
        SearchResult result = new SearchResult();
        result.setPubkey(record.pubkey());
        result.setName(record.username());
        result.setNip05(record.nip05());
        return result;
    }
}
