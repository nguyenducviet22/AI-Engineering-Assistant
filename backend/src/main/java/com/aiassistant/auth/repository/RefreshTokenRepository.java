package com.aiassistant.auth.repository;

import com.aiassistant.auth.entity.RefreshToken;
import com.aiassistant.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);
}
