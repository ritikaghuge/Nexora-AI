package com.nexora.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "uploaded_files")
public class UploadedFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    public Long conversationId;
    @Column(nullable = false) public String originalName;
    @Column(nullable = false, length = 100) public String storedName;
    @Column(length = 100) public String contentType;
    /** DOCUMENT or IMAGE */
    @Column(nullable = false, length = 20) public String kind;
    @Column(nullable = false) public long sizeBytes;
    @Lob public String extractedText;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
