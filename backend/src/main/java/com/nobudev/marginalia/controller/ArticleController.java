package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.ArticleResponse;
import com.nobudev.marginalia.dto.ArticleStateRequest;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.ArticleService;
import com.nobudev.marginalia.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;
    private final UserService userService;

    public ArticleController(ArticleService articleService, UserService userService) {
        this.articleService = articleService;
        this.userService = userService;
    }

    @GetMapping
    public Page<ArticleResponse> getArticles(
            @RequestParam(required = false) Long feedId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "false") boolean saved,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        User user = userService.getCurrentUser();

        if (saved) {
            return articleService.getSavedArticles(user.getId(), pageable);
        } else if (feedId != null) {
            return articleService.getArticlesByFeed(feedId, user.getId(), pageable);
        } else if (categoryId != null) {
            return articleService.getArticlesByCategory(categoryId, user.getId(), pageable);
        } else {
            return articleService.getAllArticles(user.getId(), pageable);
        }
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        User user = userService.getCurrentUser();
        return Map.of("unreadCount", articleService.getUnreadCount(user.getId()));
    }

    @GetMapping("/unread-counts")
    public Map<String, Object> getUnreadCounts() {
        User user = userService.getCurrentUser();
        long total = articleService.getUnreadCount(user.getId());
        Map<Long, Long> byFeed = articleService.getUnreadCountsByFeed(user.getId());
        return Map.of(
                "total", total,
                "byFeed", byFeed
        );
    }

    @GetMapping("/{id:\\d+}")
    public ArticleResponse getArticle(@PathVariable Long id) {
        User user = userService.getCurrentUser();
        return articleService.getArticle(id, user.getId());
    }

    @PatchMapping("/{id:\\d+}")
    public ResponseEntity<Void> updateArticleState(
            @PathVariable Long id,
            @RequestBody ArticleStateRequest request) {
        User user = userService.getCurrentUser();
        if (request.read() != null) {
            articleService.markAsRead(user.getId(), id, request.read());
        }
        if (request.saved() != null) {
            articleService.toggleSaved(user.getId(), id, request.saved());
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean read) {
        User user = userService.getCurrentUser();
        articleService.markAsRead(user.getId(), id, read);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/save")
    public ResponseEntity<Void> toggleSaved(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean saved) {
        User user = userService.getCurrentUser();
        articleService.toggleSaved(user.getId(), id, saved);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead(@RequestBody List<Long> articleIds) {
        User user = userService.getCurrentUser();
        articleService.markAllAsRead(user.getId(), articleIds != null ? articleIds : Collections.emptyList());
        return ResponseEntity.noContent().build();
    }
}
