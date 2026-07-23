package xyz.tcheeric.bottin.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import xyz.tcheeric.bottin.client.dto.SearchResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class SearchController {

    @GetMapping("/search")
    public String searchPage(Model model) {
        model.addAttribute("title", "Search");
        model.addAttribute("content", "search");
        return "layout";
    }

    @GetMapping("/api/v1/search")
    @ResponseBody
    public Map<String, Object> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        if (query == null || query.isBlank()) {
            return Map.of("query", query, "results", Collections.emptyList(), "total", 0);
        }

        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        List<SearchResult> results = Collections.emptyList();

        return Map.of("query", query, "results", results, "total", results.size(), "limit", cappedLimit);
    }
}
