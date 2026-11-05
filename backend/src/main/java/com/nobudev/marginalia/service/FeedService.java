package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.FeedRequest;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ArticleRepository articleRepository;

    public FeedService(FeedRepository feedRepository,
                       CategoryRepository categoryRepository) {
        this(feedRepository, categoryRepository, null);
    }

    @Autowired
    public FeedService(FeedRepository feedRepository,
                       CategoryRepository categoryRepository,
                       ArticleRepository articleRepository) {
        this.feedRepository = feedRepository;
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
    }

    public List<FeedResponse> getFeedsByUser(Long userId) {
        Map<Long, Long> filteredCounts = getFilteredCountsByFeed(userId);
        return feedRepository.findByUserIdOrderBySortOrderAscTitleAsc(userId).stream()
                .map(feed -> FeedResponse.from(feed, filteredCounts.getOrDefault(feed.getId(), 0L)))
                .toList();
    }

    public FeedResponse getFeed(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdAndUserId(feedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        long filteredCount = articleRepository != null
                ? articleRepository.countFilteredByFeedIdAndUserId(feedId, userId)
                : 0L;
        return FeedResponse.from(feed, filteredCount);
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

        String initialTitle = (request.title() != null && !request.title().isBlank())
                ? request.title().trim()
                : request.feedUrl();

        Feed feed = new Feed(
                user,
                category,
                request.feedUrl(),
                null,
                initialTitle,
                null
        );
        feed.setSortOrder(nextSortOrder);
        if (request.filterKeywords() != null) {
            feed.setFilterKeywordList(request.filterKeywords());
        }
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

        if (request.title() != null && !request.title().isBlank()) {
            feed.setTitle(request.title().trim());
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found: " + request.categoryId()));
            feed.setCategory(category);
        }

        boolean filtersUpdated = false;
        if (request.filterKeywords() != null) {
            String previous = feed.getFilterKeywords();
            feed.setFilterKeywordList(request.filterKeywords());
            filtersUpdated = !Objects.equals(previous, feed.getFilterKeywords());
        }

        feed = feedRepository.save(feed);

        if (filtersUpdated && articleRepository != null) {
            reapplyFeedFilters(feed, userId);
        }

        long filteredCount = articleRepository != null
                ? articleRepository.countFilteredByFeedIdAndUserId(feed.getId(), userId)
                : 0L;
        return FeedResponse.from(feed, filteredCount);
    }

    /**
     * Re-evaluates title filter keywords against unread and currently filtered
     * articles of the given feed using a lightweight (id, title, filtered)
     * projection and at most two bulk SQL UPDATE statements.
     */
    private void reapplyFeedFilters(Feed feed, Long userId) {
        var candidates = articleRepository.findUnreadOrFilteredTitlesByFeedIdAndUserId(feed.getId(), userId);
        if (candidates.isEmpty()) {
            return;
        }

        List<Long> toFilterIds = new ArrayList<>();
        List<Long> toUnfilterIds = new ArrayList<>();

        for (var row : candidates) {
            boolean shouldFilter = feed.matchesTitleFilter(row.getTitle());
            if (shouldFilter && !row.isFiltered()) {
                toFilterIds.add(row.getId());
            } else if (!shouldFilter && row.isFiltered()) {
                toUnfilterIds.add(row.getId());
            }
        }

        if (!toFilterIds.isEmpty()) {
            articleRepository.updateFilteredByIds(feed.getId(), toFilterIds, true);
        }
        if (!toUnfilterIds.isEmpty()) {
            articleRepository.updateFilteredByIds(feed.getId(), toUnfilterIds, false);
        }
    }

    private Map<Long, Long> getFilteredCountsByFeed(Long userId) {
        if (articleRepository == null) {
            return Map.of();
        }
        List<Object[]> rows = articleRepository.countFilteredGroupedByFeed(userId);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public void deleteFeed(Long feedId, Long userId) {
        Feed feed = feedRepository.findByIdAndUserId(feedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        feedRepository.delete(feed);
    }
}
