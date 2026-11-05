package com.nexora.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long conversationId;
    /** USER or ASSISTANT */
    @Column(nullable = false, length = 20) public String role;
    @Lob @Column(nullable = false) public String content;
    @Lob public String sourcesJson;
    @Column(length = 500) public String fileIds;
    @Column(length = 190) public String modelUsed;
    @Column(length = 30) public String modeUsed;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
