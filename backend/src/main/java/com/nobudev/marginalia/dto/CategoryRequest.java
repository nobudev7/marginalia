package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be 100 characters or less")
    String name,
    Integer sortOrder
) {}
