package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.FeedRequest;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.nobudev.marginalia.util.UrlSafetyValidator;

@Service
@Transactional(readOnly = true)
public class FeedService {

    private final FeedRepository feedRepository;
    private final CategoryRepository categoryRepository;

    public FeedService(FeedRepository feedRepository,
                       CategoryRepository categoryRepository) {
        this.feedRepository = feedRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<FeedResponse> getFeedsByUser(Long userId) {
        return feedRepository.findByUserIdOrderBySortOrderAscTitleAsc(userId).stream()
                .map(FeedResponse::from)
                .toList();
    }

    public FeedResponse getFeed(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdAndUserId(feedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        return FeedResponse.from(feed);
    }

    /**
     * Subscribes to a new feed.
     * Pure database persistence: saves the feed record and returns the response.
     * Crawling is handled independently by the client/crawler service.
     */
    @Transactional
    public FeedResponse addFeed(FeedRequest request, User user) {
        UrlSafetyValidator.validate(request.feedUrl());

        feedRepository.findByUserIdAndFeedUrl(user.getId(), request.feedUrl())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Feed already subscribed: " + request.feedUrl());
                });

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), user.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found: " + request.categoryId()));
        }

        Long targetCatId = category != null ? category.getId() : null;
        int nextSortOrder = feedRepository.findByUserIdOrderBySortOrderAscTitleAsc(user.getId()).stream()
                .filter(f -> Objects.equals(f.getCategory() != null ? f.getCategory().getId() : null, targetCatId))
                .mapToInt(Feed::getSortOrder)
                .max()
                .orElse(-1) + 1;

        Feed feed = new Feed(
                user,
                category,
                request.feedUrl(),
                null,
                request.title() != null ? request.title() : request.feedUrl(),
                null
        );
        feed.setSortOrder(nextSortOrder);
        return FeedResponse.from(feedRepository.save(feed));
    }

    @Transactional
    public List<FeedResponse> reorderFeeds(Long categoryId, List<Long> feedIds, Long userId) {
        Category targetCategory = null;
        if (categoryId != null) {
            targetCategory = categoryRepository.findByIdAndUserId(categoryId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        }

        List<Feed> userFeeds = feedRepository.findByUserIdOrderBySortOrderAscTitleAsc(userId);
        Map<Long, Feed> byId = new HashMap<>();
        for (Feed f : userFeeds) {
            byId.put(f.getId(), f);
        }

        for (Long id : feedIds) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("Feed not found: " + id);
            }
        }

        int order = 0;
        Set<Long> seen = new HashSet<>();
        List<Feed> updated = new ArrayList<>();
        for (Long id : feedIds) {
            if (seen.add(id)) {
                Feed feed = byId.get(id);
                feed.setCategory(targetCategory);
                feed.setSortOrder(order++);
                updated.add(feed);
            }
        }

        feedRepository.saveAll(updated);
        return getFeedsByUser(userId);
    }

    @Transactional
    public FeedResponse updateFeed(Long feedId, FeedRequest request, Long userId) {
        Feed feed = feedRepository.findByIdAndUserId(feedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));

        if (request.title() != null) {
            feed.setTitle(request.title());
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found: " + request.categoryId()));
            feed.setCategory(category);
        }

        feed = feedRepository.save(feed);
        return FeedResponse.from(feed);
    }

    @Transactional
    public void deleteFeed(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdAndUserId(feedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        feedRepository.delete(feed);
    }
}
