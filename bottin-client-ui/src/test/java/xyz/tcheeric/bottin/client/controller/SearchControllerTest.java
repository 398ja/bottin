package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.client.dto.SearchResult;
import xyz.tcheeric.bottin.client.service.DirectorySearchException;
import xyz.tcheeric.bottin.client.service.SearchService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    private static final String ALICE_HEX =
            "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    /**
     * Tests that the search page renders through the shared layout.
     */
    @Test
    void shouldShowSearchPage() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "search"));
    }

    /**
     * Tests that a match is serialised with the fields the page renders: the
     * key it links to and the full identifier it shows.
     */
    @Test
    void shouldReturnTheMatchesTheDirectoryHolds() throws Exception {
        // Given: the directory holds one match
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of(alice()));

        // When: searching  // Then: the result reaches the page intact
        mockMvc.perform(get("/api/v1/search").param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.results[0].pubkey").value(ALICE_HEX))
                .andExpect(jsonPath("$.results[0].nip05").value("alice@example.test"));
    }

    /**
     * Tests that an unreachable directory answers 502 rather than an empty
     * 200. Break it by catching the exception and returning an empty list and
     * this fails, which is the point: an empty 200 renders as "no profiles
     * found" and would tell the searcher the person is not registered.
     */
    @Test
    void shouldAnswerBadGatewayWhenTheDirectoryCannotBeSearched() throws Exception {
        // Given: the directory cannot be reached
        when(searchService.search(anyString(), anyInt()))
                .thenThrow(new DirectorySearchException("unreachable", new RuntimeException()));

        // When: searching  // Then: the failure is reported as a failure
        mockMvc.perform(get("/api/v1/search").param("q", "alice"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("DIRECTORY_UNAVAILABLE"))
                .andExpect(jsonPath("$.results").doesNotExist());
    }

    /**
     * Tests that a blank query is answered without the directory being asked.
     */
    @Test
    void shouldReturnEmptyResultsForBlankQuery() throws Exception {
        // Given: the service finds nothing for a blank query
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        // When: searching for nothing  // Then: an empty answer
        mockMvc.perform(get("/api/v1/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    /**
     * Tests that twenty records are requested when the caller names no limit,
     * asserted on what the service was asked for rather than on the echoed
     * field, which a handler could set without using it.
     */
    @Test
    void shouldUseDefaultLimitOfTwenty() throws Exception {
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search").param("q", "alice"))
                .andExpect(status().isOk());

        verify(searchService).search("alice", 20);
    }

    /**
     * Tests that an outsized limit is capped before it reaches the directory,
     * so one request cannot ask for the whole table.
     */
    @Test
    void shouldCapAnOutsizedLimitAtOneHundred() throws Exception {
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search").param("q", "alice").param("limit", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));

        verify(searchService).search(eq("alice"), eq(100));
    }

    /**
     * Tests that a limit below one is raised to one rather than reaching the
     * directory as a zero or negative page size.
     */
    @Test
    void shouldRaiseALimitBelowOneToOne() throws Exception {
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search").param("q", "alice").param("limit", "0"))
                .andExpect(status().isOk());

        verify(searchService).search(eq("alice"), eq(1));
    }

    private static SearchResult alice() {
        SearchResult result = new SearchResult();
        result.setPubkey(ALICE_HEX);
        result.setName("alice");
        result.setNip05("alice@example.test");
        return result;
    }
}
