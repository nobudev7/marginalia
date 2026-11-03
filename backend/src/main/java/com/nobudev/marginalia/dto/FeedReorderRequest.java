package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FeedReorderRequest(
    Long categoryId,
    @NotNull(message = "feedIds is required")
    List<Long> feedIds
) {}
