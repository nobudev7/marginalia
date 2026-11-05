package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record FeedRequest(
    @NotBlank(message = "Feed URL is required")
    String feedUrl,
    String title,
    Long categoryId,
    List<String> filterKeywords
) {
    public FeedRequest(String feedUrl, String title, Long categoryId) {
        this(feedUrl, title, categoryId, null);
    }
}
