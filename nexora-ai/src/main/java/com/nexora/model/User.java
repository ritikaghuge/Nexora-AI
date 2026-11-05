package com.nexora.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 190) public String email;
    @Column(nullable = false, length = 100) public String displayName;
    @Column(nullable = false) public String passwordHash;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
