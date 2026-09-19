package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.WhitelistEntry;
import java.time.LocalDateTime;

public record WhitelistResponse(
        Long id,
        String email,
        String note,
        String addedBy,
        LocalDateTime createdAt
) {
    public static WhitelistResponse from(WhitelistEntry entry) {
        return new WhitelistResponse(
                entry.getId(),
                entry.getEmail(),
                entry.getNote(),
                entry.getAddedBy(),
                entry.getCreatedAt()
        );
    }
}
