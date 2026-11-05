package com.nexora.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.config.AppProperties;
import com.nexora.dto.Dtos.SourceDto;
import com.nexora.exception.AiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

/** Configurable web search: wikipedia (no setup), searxng (self-hosted), brave (free API key). */
@Service
public class SearchService {
    private static final String UA = "NexoraAI/1.0 (student project; contact: local)";
    private final AppProperties props;
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public SearchService(AppProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
    }

    public String providerName() {
        String p = props.search().provider();
        return p == null || p.isBlank() ? "wikipedia" : p.trim().toLowerCase();
    }

    public List<SourceDto> search(String query, int limit) {
        String q = query.length() > 200 ? query.substring(0, 200) : query;
        return switch (providerName()) {
            case "searxng" -> searxng(q, limit);
            case "brave" -> brave(q, limit);
            case "wikipedia" -> wikipedia(q, limit);
            default -> throw new AiException("Unknown SEARCH_PROVIDER '" + providerName() + "'. Use wikipedia, searxng or brave.");
        };
    }

    private JsonNode getJson(String url, String... headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", UA);
            for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
            HttpResponse<String> r = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) throw new AiException("Search provider returned HTTP " + r.statusCode());
            return om.readTree(r.body());
        } catch (IOException e) {
            throw new AiException("Web search is unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Web search interrupted");
        } catch (IllegalArgumentException e) {
            throw new AiException("Invalid search URL configuration");
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private List<SourceDto> wikipedia(String q, int limit) {
        JsonNode n = getJson("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=" + limit
                + "&srsearch=" + enc(q));
        List<SourceDto> out = new ArrayList<>();
        for (JsonNode r : n.path("query").path("search")) {
            out.add(new SourceDto(r.path("title").asText(), "https://en.wikipedia.org/?curid=" + r.path("pageid").asLong(),
                    Jsoup.parse(r.path("snippet").asText()).text()));
        }
        return out;
    }

    private List<SourceDto> searxng(String q, int limit) {
        String base = props.search().searxngUrl();
        if (base == null || base.isBlank()) throw new AiException("SEARXNG_URL is not configured");
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        JsonNode n = getJson(base + "/search?format=json&q=" + enc(q));
        List<SourceDto> out = new ArrayList<>();
        for (JsonNode r : n.path("results")) {
            if (out.size() >= limit) break;
            out.add(new SourceDto(r.path("title").asText(), r.path("url").asText(), r.path("content").asText()));
        }
        return out;
    }

    private List<SourceDto> brave(String q, int limit) {
        String key = props.search().braveApiKey();
        if (key == null || key.isBlank()) throw new AiException("BRAVE_API_KEY is not configured");
        JsonNode n = getJson("https://api.search.brave.com/res/v1/web/search?count=" + limit + "&q=" + enc(q),
                "X-Subscription-Token", key.trim(), "Accept", "application/json");
        List<SourceDto> out = new ArrayList<>();
        for (JsonNode r : n.path("web").path("results")) {
            if (out.size() >= limit) break;
            out.add(new SourceDto(r.path("title").asText(), r.path("url").asText(),
                    Jsoup.parse(r.path("description").asText()).text()));
        }
        return out;
    }
}
