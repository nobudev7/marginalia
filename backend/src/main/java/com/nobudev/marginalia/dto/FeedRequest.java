package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedRequest(
    @NotBlank(message = "Feed URL is required")
    String feedUrl,
    String title,
    Long categoryId
) {}
