package com.nexora.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public Long userId;
    @Column(nullable = false, length = 20) public String theme = "dark";
    @Column(nullable = false) public boolean memoryEnabled = true;
    public Long defaultProviderId;
    @Column(length = 190) public String defaultModel;
    @Column(nullable = false) public double temperature = 0.7;
    @Column(length = 2000) public String customInstructions;
}
