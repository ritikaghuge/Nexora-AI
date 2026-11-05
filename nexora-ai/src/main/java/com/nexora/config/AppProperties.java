package com.nexora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexora")
public record AppProperties(String encryptionKey, String uploadDir, Ollama ollama, Search search) {
    public record Ollama(String baseUrl, String defaultModel) {}
    public record Search(String provider, String searxngUrl, String braveApiKey) {}
}
