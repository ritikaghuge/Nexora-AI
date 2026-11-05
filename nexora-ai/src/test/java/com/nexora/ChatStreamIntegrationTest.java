package com.nexora;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** End-to-end: real HTTP session + real SSE stream against a fake Ollama server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStreamIntegrationTest {
    static HttpServer fake;
    @LocalServerPort int port;

    @BeforeAll
    static void startFakeOllama() throws Exception {
        fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/api/chat", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] out = ("{\"message\":{\"content\":\"Hello \"},\"done\":false}\n"
                    + "{\"message\":{\"content\":\"world\"},\"done\":false}\n"
                    + "{\"done\":true}\n").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fake.createContext("/api/tags", ex -> {
            byte[] out = "{\"models\":[{\"name\":\"fake-model:latest\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fake.start();
    }

    @AfterAll
    static void stop() { fake.stop(0); }

    private HttpResponse<String> call(HttpClient c, String method, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        String json = body == null ? "" : TestSupport.OM.writeValueAsString(body);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return c.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient loggedInClient() throws Exception {
        HttpClient c = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String email = TestSupport.uniqueEmail();
        assertEquals(201, call(c, "POST", "/api/auth/register", Map.of("email", email, "password", TestSupport.PASSWORD, "displayName", "E2E")).statusCode());
        assertEquals(200, call(c, "POST", "/api/auth/login", Map.of("email", email, "password", TestSupport.PASSWORD)).statusCode());
        return c;
    }

    private long pointDefaultProviderTo(HttpClient c, String baseUrl) throws Exception {
        JsonNode list = TestSupport.OM.readTree(call(c, "GET", "/api/providers", null).body());
        long id = list.get(0).get("id").asLong();
        HttpResponse<String> r = call(c, "PUT", "/api/providers/" + id, Map.of("name", "Fake", "type", "OLLAMA",
                "baseUrl", baseUrl, "defaultModel", "fake-model", "preferred", true));
        assertEquals(200, r.statusCode(), r.body());
        return id;
    }

    @Test
    void streamsTokensAndStoresBothMessages() throws Exception {
        HttpClient c = loggedInClient();
        long pid = pointDefaultProviderTo(c, "http://127.0.0.1:" + fake.getAddress().getPort());

        JsonNode test = TestSupport.OM.readTree(call(c, "POST", "/api/providers/" + pid + "/test", null).body());
        assertTrue(test.get("ok").asBoolean(), test.toString());

        HttpResponse<String> r = call(c, "POST", "/api/chat/stream", Map.of("message", "Hi there", "mode", "GENERAL"));
        assertEquals(200, r.statusCode(), r.body());
        String sse = r.body();
        assertTrue(sse.contains("event:token"), sse);
        assertTrue(sse.contains("\"t\":\"Hello \"") && sse.contains("\"t\":\"world\""), sse);
        assertTrue(sse.contains("event:done"), sse);
        assertFalse(sse.contains("event:error"), sse);

        Matcher m = Pattern.compile("\"conversationId\":(\\d+)").matcher(sse);
        assertTrue(m.find());
        JsonNode msgs = TestSupport.OM.readTree(call(c, "GET", "/api/conversations/" + m.group(1) + "/messages", null).body());
        assertEquals(2, msgs.size());
        assertEquals("USER", msgs.get(0).get("role").asText());
        assertEquals("Hello world", msgs.get(1).get("content").asText());
    }

    @Test
    void smartModeRoutesAndReportsDecision() throws Exception {
        HttpClient c = loggedInClient();
        pointDefaultProviderTo(c, "http://127.0.0.1:" + fake.getAddress().getPort());
        String sse = call(c, "POST", "/api/chat/stream", Map.of("message", "Fix this Java NullPointerException bug", "mode", "SMART")).body();
        assertTrue(sse.contains("\"mode\":\"PROGRAMMING\""), sse);
    }

    @Test
    void unreachableProviderGivesFriendlyError() throws Exception {
        int closed;
        try (ServerSocket s = new ServerSocket(0)) { closed = s.getLocalPort(); }
        HttpClient c = loggedInClient();
        long pid = pointDefaultProviderTo(c, "http://127.0.0.1:" + closed);

        JsonNode test = TestSupport.OM.readTree(call(c, "POST", "/api/providers/" + pid + "/test", null).body());
        assertFalse(test.get("ok").asBoolean());

        String sse = call(c, "POST", "/api/chat/stream", Map.of("message", "Hello", "mode", "GENERAL")).body();
        assertTrue(sse.contains("event:error"), sse);
        assertTrue(sse.contains("Cannot connect"), sse);
    }
}
