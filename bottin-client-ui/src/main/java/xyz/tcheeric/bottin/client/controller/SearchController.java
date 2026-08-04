package xyz.tcheeric.bottin.client.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import xyz.tcheeric.bottin.client.dto.SearchResult;
import xyz.tcheeric.bottin.client.service.DirectorySearchException;
import xyz.tcheeric.bottin.client.service.SearchService;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final SearchService searchService;

    @GetMapping("/search")
    public String searchPage(Model model) {
        model.addAttribute("title", "Search");
        model.addAttribute("content", "search");
        return "layout";
    }

    /**
     * Searches the registered handles. Public, as the records it returns are
     * already served without authentication at {@code /.well-known/nostr.json}.
     *
     * <p>An unreachable directory answers 502 rather than an empty result, so
     * the page can say the search failed instead of reporting that nobody
     * matched.
     */
    @GetMapping("/api/v1/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        int cappedLimit = Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);

        try {
            List<SearchResult> results = searchService.search(query, cappedLimit);
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "results", results,
                    "total", results.size(),
                    "limit", cappedLimit));
        } catch (DirectorySearchException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "DIRECTORY_UNAVAILABLE"));
        }
    }
}
