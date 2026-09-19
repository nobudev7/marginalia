package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.User;

public record AuthResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl
) {
    public static AuthResponse from(User user) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }
}
