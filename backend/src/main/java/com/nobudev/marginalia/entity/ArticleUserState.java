package com.nobudev.marginalia.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "article_user_state",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_state", columnNames = {"user_id", "article_id"})
    },
    indexes = {
        @Index(name = "idx_user_read", columnList = "user_id, is_read, article_id"),
        @Index(name = "idx_user_saved", columnList = "user_id, is_saved, saved_at DESC")
    }
)
public class ArticleUserState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "is_saved", nullable = false)
    private boolean isSaved = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ArticleUserState() {
    }

    public ArticleUserState(User user, Article article) {
        this.user = user;
        this.article = article;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
        this.readAt = read ? LocalDateTime.now() : null;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public void setSaved(boolean saved) {
        isSaved = saved;
        this.savedAt = saved ? LocalDateTime.now() : null;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
