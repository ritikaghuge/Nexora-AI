package com.nexora.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.exception.AiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Works with any OpenAI-style /chat/completions API: Groq, OpenRouter, LM Studio, vLLM, llama.cpp server, etc. */
@Component
public class OpenAiCompatibleProvider extends AbstractHttpProvider {
    public OpenAiCompatibleProvider(ObjectMapper om) { super(om); }

    @Override public String type() { return "OPENAI_COMPATIBLE"; }

    private HttpRequest.Builder auth(HttpRequest.Builder b, ProviderSettings s) {
        if (s.apiKey() != null && !s.apiKey().isBlank()) b.header("Authorization", "Bearer " + s.apiKey().trim());
        return b;
    }

    private String payload(String model, List<ChatMsg> msgs, boolean stream, double temp) {
        List<Map<String, Object>> ms = new ArrayList<>();
        for (ChatMsg m : msgs) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("role", m.role());
            if (m.images().isEmpty()) {
                o.put("content", m.content());
            } else {
                List<Object> parts = new ArrayList<>();
                parts.add(Map.of("type", "text", "text", m.content()));
                for (ImageData i : m.images()) {
                    parts.add(Map.of("type", "image_url",
                            "image_url", Map.of("url", "data:" + i.mime() + ";base64," + i.base64())));
                }
                o.put("content", parts);
            }
            ms.add(o);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", ms);
        body.put("stream", stream);
        body.put("temperature", temp);
        try {
            return om.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiException("Could not build request");
        }
    }

    @Override
    public void stream(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature, TokenSink sink) {
        HttpRequest r = auth(request(base(s) + "/chat/completions", Duration.ofMinutes(15)), s)
                .POST(HttpRequest.BodyPublishers.ofString(payload(model, msgs, true, temperature))).build();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(open(r, s), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if (data.equals("[DONE]")) break;
                JsonNode n = om.readTree(data);
                if (n.has("error")) throw new AiException(n.path("error").path("message").asText("Provider error"));
                JsonNode c = n.path("choices").path(0).path("delta").path("content");
                if (c.isTextual() && !c.asText().isEmpty()) emit(sink, c.asText());
            }
        } catch (IOException e) {
            throw new AiException("Connection to model lost: " + e.getMessage());
        }
    }

    @Override
    public String complete(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature) {
        HttpRequest r = auth(request(base(s) + "/chat/completions", Duration.ofMinutes(5)), s)
                .POST(HttpRequest.BodyPublishers.ofString(payload(model, msgs, false, temperature))).build();
        try {
            JsonNode n = om.readTree(readAll(open(r, s)));
            return n.path("choices").path(0).path("message").path("content").asText("");
        } catch (JsonProcessingException e) {
            throw new AiException("Unexpected response from provider");
        }
    }

    @Override
    public List<String> listModels(ProviderSettings s) {
        HttpRequest r = auth(request(base(s) + "/models", Duration.ofSeconds(15)), s).GET().build();
        try {
            JsonNode n = om.readTree(readAll(open(r, s)));
            List<String> out = new ArrayList<>();
            for (JsonNode m : n.path("data")) out.add(m.path("id").asText());
            return out;
        } catch (JsonProcessingException e) {
            throw new AiException("Unexpected response from provider (check the base URL, it usually ends with /v1)");
        }
    }
}
