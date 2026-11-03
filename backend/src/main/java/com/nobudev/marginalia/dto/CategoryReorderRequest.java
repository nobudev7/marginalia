package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CategoryReorderRequest(
    @NotNull(message = "categoryIds is required")
    List<Long> categoryIds
) {}
