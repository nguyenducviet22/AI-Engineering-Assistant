package com.aiassistant.auth.security;

import com.aiassistant.auth.entity.UserRole;

public record CurrentUser(Long id, String email, UserRole role) {
}
