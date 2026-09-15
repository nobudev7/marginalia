package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.ArticleResponse;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.ArticleUserState;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.ArticleUserStateRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleUserStateRepository stateRepository;
    private final UserRepository userRepository;

    public ArticleService(ArticleRepository articleRepository,
                          ArticleUserStateRepository stateRepository,
                          UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.stateRepository = stateRepository;
        this.userRepository = userRepository;
    }

    public Page<ArticleResponse> getArticlesByFeed(Long feedId, Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findByFeedIdOrderByPublishedAtDesc(feedId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getArticlesByCategory(Long categoryId, Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findByFeedCategoryIdOrderByPublishedAtDesc(categoryId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getAllArticles(Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findByFeedUserIdOrderByPublishedAtDesc(userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getSavedArticles(Long userId, Pageable pageable) {
        Page<ArticleUserState> savedStates = stateRepository.findByUserIdAndIsSavedTrueOrderBySavedAtDesc(userId, pageable);
        return savedStates.map(state -> ArticleResponse.from(state.getArticle(), state.isRead(), true));
    }

    public long getUnreadCount(Long userId) {
        return articleRepository.countUnreadByUserId(userId);
    }

    public ArticleResponse getArticle(Long articleId, Long userId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));
        var state = stateRepository.findByUserIdAndArticleId(userId, articleId);
        boolean isRead = state.map(ArticleUserState::isRead).orElse(false);
        boolean isSaved = state.map(ArticleUserState::isSaved).orElse(false);
        return ArticleResponse.from(article, isRead, isSaved);
    }

    @Transactional
    public void markAsRead(Long userId, Long articleId, boolean read) {
        ArticleUserState state = getOrCreateState(userId, articleId);
        state.setRead(read);
        stateRepository.save(state);
    }

    @Transactional
    public void toggleSaved(Long userId, Long articleId, boolean saved) {
        ArticleUserState state = getOrCreateState(userId, articleId);
        state.setSaved(saved);
        stateRepository.save(state);
    }

    @Transactional
    public void markAllAsRead(Long userId, List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return;
        }

        // 1 query: batch-fetch all existing states for the given article IDs
        List<ArticleUserState> existingStates = stateRepository.findByUserIdAndArticleIdIn(userId, articleIds);

        Map<Long, ArticleUserState> stateMap = existingStates.stream()
                .collect(Collectors.toMap(s -> s.getArticle().getId(), s -> s));

        // Update existing states and create new ones in memory
        List<ArticleUserState> toSave = new java.util.ArrayList<>(articleIds.size());
        User userRef = userRepository.getReferenceById(userId);

        for (Long articleId : articleIds) {
            ArticleUserState state = stateMap.get(articleId);
            if (state == null) {
                state = new ArticleUserState(userRef, articleRepository.getReferenceById(articleId));
            }
            state.setRead(true);
            toSave.add(state);
        }

        // 1 batch save: all inserts and updates in a single flush
        stateRepository.saveAll(toSave);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private ArticleUserState getOrCreateState(Long userId, Long articleId) {
        return stateRepository.findByUserIdAndArticleId(userId, articleId)
                .orElseGet(() -> {
                    User user = userRepository.getReferenceById(userId);
                    Article article = articleRepository.getReferenceById(articleId);
                    return new ArticleUserState(user, article);
                });
    }

    /**
     * Batch-loads user state (read/saved) for a page of articles and maps
     * them to ArticleResponse DTOs. Uses a single query to load all states
     * for the page, avoiding N+1 queries.
     */
    private Page<ArticleResponse> enrichWithUserState(Page<Article> articles, Long userId) {
        List<Long> articleIds = articles.getContent().stream()
                .map(Article::getId)
                .toList();

        Map<Long, ArticleUserState> stateMap = stateRepository
                .findByUserIdAndArticleIdIn(userId, articleIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getArticle().getId(),
                        s -> s));

        return articles.map(article -> {
            ArticleUserState state = stateMap.get(article.getId());
            boolean isRead = state != null && state.isRead();
            boolean isSaved = state != null && state.isSaved();
            return ArticleResponse.from(article, isRead, isSaved);
        });
    }
}
