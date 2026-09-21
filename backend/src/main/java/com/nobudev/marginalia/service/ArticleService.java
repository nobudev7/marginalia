package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.ArticleResponse;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.ArticleUserState;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.ArticleUserStateRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

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
        Page<Article> articles = articleRepository.findByFeedIdAndFeedUserIdOrderByPublishedAtDesc(feedId, userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getArticlesByCategory(Long categoryId, Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findByFeedCategoryIdAndFeedUserIdOrderByPublishedAtDesc(categoryId, userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getAllArticles(Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findByFeedUserIdOrderByPublishedAtDesc(userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getUnreadArticles(Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findUnreadByUserId(userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getUnreadArticlesByFeed(Long feedId, Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findUnreadByFeedIdAndUserId(feedId, userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getUnreadArticlesByCategory(Long categoryId, Long userId, Pageable pageable) {
        Page<Article> articles = articleRepository.findUnreadByCategoryIdAndUserId(categoryId, userId, pageable);
        return enrichWithUserState(articles, userId);
    }

    public Page<ArticleResponse> getSavedArticles(Long userId, Pageable pageable) {
        Pageable adaptedPageable = remapSortForSavedArticles(pageable);
        Page<ArticleUserState> savedStates = stateRepository.findByUserIdAndIsSavedTrue(userId, adaptedPageable);
        return savedStates.map(state -> ArticleResponse.from(state.getArticle(), state.isRead(), true));
    }

    private Pageable remapSortForSavedArticles(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "savedAt"));
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if ("publishedAt".equalsIgnoreCase(order.getProperty())) {
                // If caller passed publishedAt (e.g. from controller default @PageableDefault),
                // remap to savedAt for bookmarked articles view.
                orders.add(new Sort.Order(order.getDirection(), "savedAt"));
            } else {
                orders.add(order);
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    public long getUnreadCount(Long userId) {
        return articleRepository.countUnreadByUserId(userId);
    }

    public java.util.Map<Long, Long> getUnreadCountsByFeed(Long userId) {
        java.util.List<Object[]> rows = articleRepository.countUnreadGroupedByFeed(userId);
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public ArticleResponse getArticle(Long articleId, Long userId) {
        Article article = articleRepository.findByIdAndFeedUserId(articleId, userId)
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

        // Filter to only articles belonging to feeds owned by this user (prevents IDOR)
        List<Long> ownedIds = articleRepository.findOwnedArticleIds(articleIds, userId);
        if (ownedIds.isEmpty()) {
            return;
        }

        // 1 query: batch-fetch all existing states for the owned article IDs
        List<ArticleUserState> existingStates = stateRepository.findByUserIdAndArticleIdIn(userId, ownedIds);

        Map<Long, ArticleUserState> stateMap = existingStates.stream()
                .collect(Collectors.toMap(s -> s.getArticle().getId(), s -> s));

        // Update existing states and create new ones in memory
        List<ArticleUserState> toSave = new java.util.ArrayList<>(ownedIds.size());
        User userRef = userRepository.getReferenceById(userId);

        for (Long articleId : ownedIds) {
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

    @Transactional
    public void markAllAsReadForUser(Long userId) {
        List<Long> unreadIds = articleRepository.findUnreadArticleIdsByUserId(userId);
        markAllAsRead(userId, unreadIds);
    }

    @Transactional
    public void markFeedAsRead(Long userId, Long feedId) {
        List<Long> unreadIds = articleRepository.findUnreadArticleIdsByFeedIdAndUserId(feedId, userId);
        markAllAsRead(userId, unreadIds);
    }

    @Transactional
    public void markCategoryAsRead(Long userId, Long categoryId) {
        List<Long> unreadIds = articleRepository.findUnreadArticleIdsByCategoryIdAndUserId(categoryId, userId);
        markAllAsRead(userId, unreadIds);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private ArticleUserState getOrCreateState(Long userId, Long articleId) {
        return stateRepository.findByUserIdAndArticleId(userId, articleId)
                .orElseGet(() -> {
                    Article article = articleRepository.findByIdAndFeedUserId(articleId, userId)
                            .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));
                    User user = userRepository.getReferenceById(userId);
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
