package com.aiassistant.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true))
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;
    @Column(nullable = false, unique = true)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant revokedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke() { this.revokedAt = Instant.now(); }
}
