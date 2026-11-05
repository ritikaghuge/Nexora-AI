package com.nexora.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memory_records")
public class MemoryRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false, length = 1000) public String content;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
