package com.nexora.controller;

import com.nexora.dto.Dtos.*;
import com.nexora.exception.ApiException;
import com.nexora.model.User;
import com.nexora.security.CurrentUserService;
import com.nexora.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthenticationManager authManager;
    private final CurrentUserService current;
    private final HttpSessionSecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth, AuthenticationManager authManager, CurrentUserService current) {
        this.auth = auth;
        this.authManager = authManager;
        this.current = current;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(r);
    }

    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest r, HttpServletRequest req, HttpServletResponse res) {
        Authentication a = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(r.email().trim().toLowerCase(), r.password()));
        // Prevent session fixation: start a fresh session on login.
        HttpSession old = req.getSession(false);
        if (old != null) req.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(a);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, res);
        return AuthService.toDto(current.require(a));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public UserDto me(Authentication a) {
        return AuthService.toDto(current.require(a));
    }

    @PutMapping("/profile")
    public UserDto profile(@Valid @RequestBody ProfileRequest r, Authentication a) {
        return auth.updateProfile(current.require(a), r.displayName());
    }

    @PostMapping("/password")
    public Map<String, Boolean> password(@Valid @RequestBody PasswordRequest r, Authentication a) {
        User u = current.require(a);
        auth.changePassword(u, r);
        return Map.of("ok", true);
    }
}
