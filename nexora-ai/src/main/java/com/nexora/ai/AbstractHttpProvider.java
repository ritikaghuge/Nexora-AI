package com.nexora.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.exception.AiCancelledException;
import com.nexora.exception.AiException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

abstract class AbstractHttpProvider implements AiProvider {
    protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    protected final ObjectMapper om;

    protected AbstractHttpProvider(ObjectMapper om) { this.om = om; }

    protected String base(ProviderSettings s) {
        String b = s.baseUrl() == null ? "" : s.baseUrl().trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    protected HttpRequest.Builder request(String url, Duration timeout) {
        try {
            return HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Content-Type", "application/json");
        } catch (IllegalArgumentException e) {
            throw new AiException("Invalid provider URL: " + url);
        }
    }

    /** Sends the request and returns the body stream, translating failures into friendly AiExceptions. */
    protected InputStream open(HttpRequest r, ProviderSettings s) {
        try {
            HttpResponse<InputStream> resp = http.send(r, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String body = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new AiException(describe(s, resp.statusCode(), body));
            }
            return resp.body();
        } catch (ConnectException e) {
            throw new AiException("Cannot connect to " + s.name() + " at " + s.baseUrl()
                    + ". Make sure it is running (for Ollama: start the Ollama app or run 'ollama serve'), "
                    + "or switch to another provider in Settings > Providers.");
        } catch (HttpTimeoutException e) {
            throw new AiException("The AI provider timed out. The model may still be loading; try again in a moment.");
        } catch (IOException e) {
            throw new AiException("Network error while contacting " + s.name() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Request interrupted");
        }
    }

    private String describe(ProviderSettings s, int status, String body) {
        String detail = body == null ? "" : body.strip();
        try {
            JsonNode n = om.readTree(body);
            if (n.path("error").isTextual()) detail = n.path("error").asText();
            else if (n.path("error").path("message").isTextual()) detail = n.path("error").path("message").asText();
        } catch (Exception ignored) { /* keep raw body */ }
        if (detail.length() > 300) detail = detail.substring(0, 300) + "...";
        String hint = switch (status) {
            case 401, 403 -> " (check the API key in Settings > Providers)";
            case 404 -> " (model or endpoint not found; for Ollama run 'ollama pull <model>')";
            case 429 -> " (rate limit reached; wait a bit or switch provider)";
            default -> "";
        };
        return s.name() + " returned HTTP " + status + ": " + detail + hint;
    }

    protected void emit(TokenSink sink, String token) {
        try {
            sink.accept(token);
        } catch (IOException e) {
            throw new AiCancelledException();
        }
    }

    protected String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiException("Connection to model lost: " + e.getMessage());
        }
    }
}
