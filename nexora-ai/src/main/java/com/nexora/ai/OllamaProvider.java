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

@Component
public class OllamaProvider extends AbstractHttpProvider {
    public OllamaProvider(ObjectMapper om) { super(om); }

    @Override public String type() { return "OLLAMA"; }

    private String payload(String model, List<ChatMsg> msgs, boolean stream, double temp) {
        List<Map<String, Object>> ms = new ArrayList<>();
        for (ChatMsg m : msgs) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("role", m.role());
            o.put("content", m.content());
            if (!m.images().isEmpty()) o.put("images", m.images().stream().map(ImageData::base64).toList());
            ms.add(o);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", ms);
        body.put("stream", stream);
        body.put("options", Map.of("temperature", temp));
        try {
            return om.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AiException("Could not build request");
        }
    }

    @Override
    public void stream(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature, TokenSink sink) {
        HttpRequest r = request(base(s) + "/api/chat", Duration.ofMinutes(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload(model, msgs, true, temperature))).build();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(open(r, s), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode n = om.readTree(line);
                if (n.hasNonNull("error")) throw new AiException(n.get("error").asText());
                String t = n.path("message").path("content").asText("");
                if (!t.isEmpty()) emit(sink, t);
                if (n.path("done").asBoolean(false)) break;
            }
        } catch (IOException e) {
            throw new AiException("Connection to model lost: " + e.getMessage());
        }
    }

    @Override
    public String complete(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature) {
        HttpRequest r = request(base(s) + "/api/chat", Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(payload(model, msgs, false, temperature))).build();
        try {
            JsonNode n = om.readTree(readAll(open(r, s)));
            if (n.hasNonNull("error")) throw new AiException(n.get("error").asText());
            return n.path("message").path("content").asText("");
        } catch (JsonProcessingException e) {
            throw new AiException("Unexpected response from Ollama");
        }
    }

    @Override
    public List<String> listModels(ProviderSettings s) {
        HttpRequest r = request(base(s) + "/api/tags", Duration.ofSeconds(15)).GET().build();
        try {
            JsonNode n = om.readTree(readAll(open(r, s)));
            List<String> out = new ArrayList<>();
            for (JsonNode m : n.path("models")) out.add(m.path("name").asText());
            return out;
        } catch (JsonProcessingException e) {
            throw new AiException("Unexpected response from Ollama (is this really an Ollama server?)");
        }
    }
}
