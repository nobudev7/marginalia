package com.nobudev.marginalia.dto;

import com.nobudev.marginalia.entity.Article;

import java.time.LocalDateTime;

public record ArticleResponse(
    Long id,
    Long feedId,
    String feedTitle,
    String feedIconUrl,
    String guid,
    String title,
    String author,
    String content,
    String summary,
    String articleUrl,
    String imageUrl,
    LocalDateTime publishedAt,
    boolean isRead,
    boolean isSaved
) {
    public static ArticleResponse from(Article article, boolean isRead, boolean isSaved) {
        return new ArticleResponse(
            article.getId(),
            article.getFeed().getId(),
            article.getFeed().getTitle(),
            article.getFeed().getIconUrl(),
            article.getGuid(),
            article.getTitle(),
            article.getAuthor(),
            article.getContent(),
            article.getSummary(),
            article.getArticleUrl(),
            article.getImageUrl(),
            article.getPublishedAt(),
            isRead,
            isSaved
        );
    }

    public static ArticleResponse from(Article article) {
        return from(article, false, false);
    }
}
