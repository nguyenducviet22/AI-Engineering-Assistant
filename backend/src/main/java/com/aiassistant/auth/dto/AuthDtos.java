package com.aiassistant.auth.dto;

import com.aiassistant.auth.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String fullName) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 500) String avatar) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @Size(min = 8, max = 128) String newPassword) {}

    public record TokenResponse(String accessToken, String refreshToken, UserProfile user) {}

    public record UserProfile(Long id, String email, String fullName, String avatar, UserRole role, Instant createdAt) {}
}
