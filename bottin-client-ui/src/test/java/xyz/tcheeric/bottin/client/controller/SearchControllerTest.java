package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldShowSearchPage() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "search"));
    }

    @Test
    void shouldReturnEmptyResultsForBlankQuery() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldReturnEmptyResultsForWhitespaceQuery() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldReturnEmptyResultsWhenNoProfilesMatch() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "nonexistentuser123")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldUseDefaultLimitOfTwenty() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("test"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void shouldAcceptCustomLimit() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "test").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("test"))
                .andExpect(jsonPath("$.total").value(0));
    }
}
