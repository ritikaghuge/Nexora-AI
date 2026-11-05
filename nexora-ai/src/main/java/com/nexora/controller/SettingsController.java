package com.nexora.controller;

import com.nexora.dto.Dtos.SettingsDto;
import com.nexora.security.CurrentUserService;
import com.nexora.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settings;
    private final CurrentUserService current;

    public SettingsController(SettingsService settings, CurrentUserService current) {
        this.settings = settings;
        this.current = current;
    }

    @GetMapping
    public SettingsDto get(Authentication a) {
        return settings.toDto(settings.get(current.require(a).id));
    }

    @PutMapping
    public SettingsDto update(@Valid @RequestBody SettingsDto d, Authentication a) {
        return settings.update(current.require(a).id, d);
    }
}
