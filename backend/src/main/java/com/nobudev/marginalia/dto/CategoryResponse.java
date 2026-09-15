package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.Category;

import java.time.LocalDateTime;

public record CategoryResponse(
    Long id,
    String name,
    int sortOrder,
    LocalDateTime createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSortOrder(),
            category.getCreatedAt()
        );
    }
}
