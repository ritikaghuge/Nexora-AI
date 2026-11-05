package com.nexora.security;

import com.nexora.exception.ApiException;
import com.nexora.model.User;
import com.nexora.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) { this.users = users; }

    public User require(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in");
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired"));
    }
}
