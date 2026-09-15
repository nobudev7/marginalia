package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.dto.FeedRequest;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.FeedCrawlerService;
import com.nobudev.marginalia.service.FeedService;
import com.nobudev.marginalia.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feeds")
public class FeedController {

    private final FeedService feedService;
    private final FeedCrawlerService crawlerService;
    private final UserService userService;

    public FeedController(FeedService feedService,
                          FeedCrawlerService crawlerService,
                          UserService userService) {
        this.feedService = feedService;
        this.crawlerService = crawlerService;
        this.userService = userService;
    }

    @GetMapping
    public List<FeedResponse> getFeeds() {
        User user = userService.getCurrentUser();
        return feedService.getFeedsByUser(user.getId());
    }

    @GetMapping("/{id}")
    public FeedResponse getFeed(@PathVariable Long id) {
        return feedService.getFeed(id);
    }

    @PostMapping
    public ResponseEntity<FeedResponse> addFeed(@Valid @RequestBody FeedRequest request) {
        User user = userService.getCurrentUser();
        FeedResponse response = feedService.addFeed(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public FeedResponse updateFeed(@PathVariable Long id, @Valid @RequestBody FeedRequest request) {
        return feedService.updateFeed(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeed(@PathVariable Long id) {
        feedService.deleteFeed(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh")
    public CrawlResult refreshFeed(@PathVariable Long id) {
        return crawlerService.crawlFeed(id);
    }
}
