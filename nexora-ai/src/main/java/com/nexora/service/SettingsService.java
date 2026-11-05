package com.nexora.service;

import com.nexora.dto.Dtos.SettingsDto;
import com.nexora.model.UserSettings;
import com.nexora.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private final UserSettingsRepository repo;

    public SettingsService(UserSettingsRepository repo) { this.repo = repo; }

    public UserSettings get(Long userId) {
        return repo.findByUserId(userId).orElseGet(() -> {
            UserSettings s = new UserSettings();
            s.userId = userId;
            return repo.save(s);
        });
    }

    public SettingsDto toDto(UserSettings s) {
        return new SettingsDto(s.theme, s.memoryEnabled, s.defaultProviderId, s.defaultModel, s.temperature, s.customInstructions);
    }

    public SettingsDto update(Long userId, SettingsDto d) {
        UserSettings s = get(userId);
        s.theme = d.theme() == null ? "dark" : d.theme();
        s.memoryEnabled = d.memoryEnabled();
        s.defaultProviderId = d.defaultProviderId();
        s.defaultModel = blankToNull(d.defaultModel());
        s.temperature = d.temperature();
        s.customInstructions = blankToNull(d.customInstructions());
        return toDto(repo.save(s));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
