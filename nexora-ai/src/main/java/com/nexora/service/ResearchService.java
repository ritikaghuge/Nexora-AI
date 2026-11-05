package com.nexora.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.ai.AiProvider;
import com.nexora.ai.ChatMsg;
import com.nexora.ai.ProviderSettings;
import com.nexora.dto.Dtos.SourceDto;
import com.nexora.exception.AiException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Deep Research mode: plan queries -> search several times -> read pages -> hand numbered sources to the model. */
@Service
public class ResearchService {
    private static final Logger log = LoggerFactory.getLogger(ResearchService.class);

    @FunctionalInterface
    public interface StatusSink { void status(String s) throws IOException; }

    private final SearchService search;
    private final PageFetcher fetcher;
    private final ObjectMapper om;

    public ResearchService(SearchService search, PageFetcher fetcher, ObjectMapper om) {
        this.search = search;
        this.fetcher = fetcher;
        this.om = om;
    }

    public List<SourceDto> gather(AiProvider provider, ProviderSettings ps, String model, String question, StatusSink status) throws IOException {
        status.status("Planning search queries...");
        List<String> queries = planQueries(provider, ps, model, question);
        Map<String, SourceDto> found = new LinkedHashMap<>();
        String lastError = null;
        for (String q : queries) {
            status.status("Searching: " + q);
            try {
                for (SourceDto s : search.search(q, 5)) found.putIfAbsent(s.url(), s);
            } catch (AiException e) {
                lastError = e.getMessage();
                log.warn("Search failed for '{}': {}", q, e.getMessage());
            }
        }
        if (found.isEmpty() && lastError != null) throw new AiException("Web search failed: " + lastError);
        List<SourceDto> top = new ArrayList<>(found.values()).subList(0, Math.min(6, found.size()));
        List<SourceDto> enriched = new ArrayList<>();
        int pages = 0;
        for (SourceDto s : top) {
            String text = s.snippet();
            if (pages < 4) {
                status.status("Reading: " + host(s.url()));
                Optional<String> page = fetcher.fetchText(s.url(), 3500);
                if (page.isPresent()) { text = page.get(); pages++; }
            }
            enriched.add(new SourceDto(s.title(), s.url(), text));
        }
        return enriched;
    }

    private List<String> planQueries(AiProvider p, ProviderSettings ps, String model, String question) {
        List<String> fallback = List.of(question.length() > 150 ? question.substring(0, 150) : question);
        try {
            String reply = p.complete(ps, model, List.of(
                    ChatMsg.of("system", "You write web search queries. Reply with ONLY a JSON array of 3 short, distinct search queries "
                            + "that together cover the user's question. No explanation, no markdown."),
                    ChatMsg.of("user", question)), 0.2);
            int a = reply.indexOf('['), b = reply.lastIndexOf(']');
            if (a >= 0 && b > a) {
                String[] arr = om.readValue(reply.substring(a, b + 1), String[].class);
                List<String> out = new ArrayList<>();
                for (String s : arr) if (s != null && !s.isBlank() && out.size() < 3) out.add(s.length() > 150 ? s.substring(0, 150) : s.trim());
                if (!out.isEmpty()) return out;
            }
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Query planning fallback: {}", e.getMessage());
        }
        return fallback;
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return url; }
    }
}
