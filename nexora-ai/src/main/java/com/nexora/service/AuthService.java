package com.nexora.service;

import com.nexora.dto.Dtos.PasswordRequest;
import com.nexora.dto.Dtos.RegisterRequest;
import com.nexora.dto.Dtos.UserDto;
import com.nexora.exception.ApiException;
import com.nexora.model.User;
import com.nexora.model.UserSettings;
import com.nexora.repository.UserRepository;
import com.nexora.repository.UserSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder encoder;
    private final ProviderService providers;

    public AuthService(UserRepository users, UserSettingsRepository settings, PasswordEncoder encoder, ProviderService providers) {
        this.users = users;
        this.settings = settings;
        this.encoder = encoder;
        this.providers = providers;
    }

    @Transactional
    public UserDto register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        User u = new User();
        u.email = email;
        u.displayName = r.displayName().trim();
        u.passwordHash = encoder.encode(r.password());
        users.save(u);
        UserSettings s = new UserSettings();
        s.userId = u.id;
        settings.save(s);
        providers.createDefault(u.id);
        return toDto(u);
    }

    @Transactional
    public UserDto updateProfile(User u, String displayName) {
        u.displayName = displayName.trim();
        return toDto(users.save(u));
    }

    @Transactional
    public void changePassword(User u, PasswordRequest r) {
        if (!encoder.matches(r.currentPassword(), u.passwordHash)) throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        u.passwordHash = encoder.encode(r.newPassword());
        users.save(u);
    }

    public static UserDto toDto(User u) { return new UserDto(u.id, u.email, u.displayName); }
}
