package com.aiassistant.auth.controller;

import com.aiassistant.auth.dto.AuthDtos.*;
import com.aiassistant.auth.service.AuthService;
import com.aiassistant.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/auth/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/auth/logout")
    void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/users/me")
    UserProfile me() {
        return authService.profile(SecurityUtils.currentUser().id());
    }

    @PutMapping("/users/me")
    UserProfile updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(SecurityUtils.currentUser().id(), request);
    }

    @PutMapping("/users/me/password")
    void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUser().id(), request);
    }
}
