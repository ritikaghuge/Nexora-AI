package com.nexora.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_provider_configs")
public class AiProviderConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false, length = 100) public String name;
    /** OLLAMA or OPENAI_COMPATIBLE */
    @Column(nullable = false, length = 30) public String type;
    @Column(nullable = false, length = 300) public String baseUrl;
    /** AES-GCM encrypted API key (never stored in plain text). */
    @Lob public String apiKeyEnc;
    @Column(length = 190) public String defaultModel;
    @Column(length = 190) public String visionModel;
    @Column(length = 190) public String codeModel;
    @Column(nullable = false) public boolean preferred;
}
