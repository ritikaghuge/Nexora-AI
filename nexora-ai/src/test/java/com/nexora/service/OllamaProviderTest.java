package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.ai.ChatMsg;
import com.nexora.ai.OllamaProvider;
import com.nexora.ai.ProviderSettings;
import com.nexora.exception.AiCancelledException;
import com.nexora.exception.AiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** AI provider connectivity tests against a local fake Ollama server (no real model needed). */
class OllamaProviderTest {
    static HttpServer fake;
    final OllamaProvider provider = new OllamaProvider(new ObjectMapper());

    @BeforeAll
    static void start() throws Exception {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/api/chat", ex -> {
            String req = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean stream = req.contains("\"stream\":true");
            String body = stream
                    ? "{\"message\":{\"content\":\"A\"},\"done\":false}\n{\"message\":{\"content\":\"B\"},\"done\":false}\n{\"message\":{\"content\":\"C\"},\"done\":false}\n{\"done\":true}\n"
                    : "{\"message\":{\"content\":\"complete answer\"},\"done\":true}";
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fake.createContext("/api/tags", ex -> {
            byte[] out = "{\"models\":[{\"name\":\"llama3.2:latest\"},{\"name\":\"qwen2.5-coder:7b\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fake.start();
    }

    @AfterAll
    static void stop() { fake.stop(0); }

    private ProviderSettings live() { return new ProviderSettings("OLLAMA", "http://127.0.0.1:" + fake.getAddress().getPort() + "/", null, "Fake"); }
    private List<ChatMsg> msgs() { return List.of(ChatMsg.of("user", "hi")); }

    @Test
    void streamsTokensInOrder() {
        List<String> got = new ArrayList<>();
        provider.stream(live(), "m", msgs(), 0.5, got::add);
        assertEquals(List.of("A", "B", "C"), got);
    }

    @Test
    void completeReturnsFullText() {
        assertEquals("complete answer", provider.complete(live(), "m", msgs(), 0.5));
    }

    @Test
    void listsModels() {
        assertEquals(List.of("llama3.2:latest", "qwen2.5-coder:7b"), provider.listModels(live()));
    }

    @Test
    void stopsWhenSinkThrowsIoException() {
        List<String> got = new ArrayList<>();
        assertThrows(AiCancelledException.class, () -> provider.stream(live(), "m", msgs(), 0.5, t -> {
            got.add(t);
            throw new IOException("client gone");
        }));
        assertEquals(1, got.size());
    }

    @Test
    void unreachableServerGivesFriendlyMessage() throws Exception {
        int closed;
        try (ServerSocket s = new ServerSocket(0)) { closed = s.getLocalPort(); }
        ProviderSettings dead = new ProviderSettings("OLLAMA", "http://127.0.0.1:" + closed, null, "Dead");
        AiException e = assertThrows(AiException.class, () -> provider.listModels(dead));
        assertTrue(e.getMessage().contains("Cannot connect"), e.getMessage());
    }
}
