package com.aiassistant.auth.service;

import com.aiassistant.auth.dto.AuthDtos.*;
import com.aiassistant.auth.entity.RefreshToken;
import com.aiassistant.auth.entity.User;
import com.aiassistant.auth.repository.RefreshTokenRepository;
import com.aiassistant.auth.repository.UserRepository;
import com.aiassistant.auth.security.JwtService;
import com.aiassistant.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenDays;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, @Value("${app.security.refresh-token-days}") long refreshTokenDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email Already Exists", "Email is already registered.");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName()));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password.");
        }
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken current = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid refresh token."));
        if (!current.isActive(Instant.now())) {
            if (current.getRevokedAt() != null) {
                refreshTokens.deleteByUser(current.getUser());
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Refresh token is expired or revoked.");
        }
        current.revoke();
        return issueTokens(current.getUser());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokens.findByTokenHash(hash(request.refreshToken())).ifPresent(RefreshToken::revoke);
    }

    public UserProfile profile(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User Not Found", "Current user was not found."));
        return toProfile(user);
    }

    @Transactional
    public UserProfile updateProfile(Long userId, UpdateProfileRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User Not Found", "Current user was not found."));
        user.setFullName(request.fullName());
        user.setAvatar(request.avatar());
        return toProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User Not Found", "Current user was not found."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Current password is invalid.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.deleteByUser(user);
    }

    private TokenResponse issueTokens(User user) {
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS)));
        return new TokenResponse(jwtService.createAccessToken(user), rawRefresh, toProfile(user));
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getAvatar(), user.getRole(), user.getCreatedAt());
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token.", ex);
        }
    }
}
