package com.nobudev.marginalia.dto;

public record CrawlResult(
    int newArticles,
    int totalEntries,
    boolean notModified,
    String error
) {
    public boolean isSuccess() {
        return error == null;
    }
}
