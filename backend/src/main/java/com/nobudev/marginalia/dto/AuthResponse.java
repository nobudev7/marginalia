package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.User;

public record AuthResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        boolean isAdmin
) {
    public static AuthResponse from(User user) {
        return from(user, false);
    }

    public static AuthResponse from(User user, boolean isAdmin) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                isAdmin
        );
    }
}
