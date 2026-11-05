package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.Feed;

import java.time.LocalDateTime;
import java.util.List;

public record FeedResponse(
    Long id,
    String feedUrl,
    String siteUrl,
    String title,
    String description,
    String iconUrl,
    Long categoryId,
    String categoryName,
    int sortOrder,
    List<String> filterKeywords,
    long filteredCount,
    LocalDateTime lastFetchedAt,
    int fetchErrorCount,
    String lastErrorMessage,
    LocalDateTime createdAt
) {
    public static FeedResponse from(Feed feed) {
        return from(feed, 0L);
    }

    public static FeedResponse from(Feed feed, long filteredCount) {
        return new FeedResponse(
            feed.getId(),
            feed.getFeedUrl(),
            feed.getSiteUrl(),
            feed.getTitle(),
            feed.getDescription(),
            feed.getIconUrl(),
            feed.getCategory() != null ? feed.getCategory().getId() : null,
            feed.getCategory() != null ? feed.getCategory().getName() : null,
            feed.getSortOrder(),
            feed.getFilterKeywordList(),
            filteredCount,
            feed.getLastFetchedAt(),
            feed.getFetchErrorCount(),
            feed.getLastErrorMessage(),
            feed.getCreatedAt()
        );
    }
}
