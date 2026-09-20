package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.dto.FeedRequest;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that FeedService operates strictly as a database CRUD service:
 * addFeed persists the feed without initiating any external network crawl.
 */
@SpringBootTest
class FeedServiceTransactionTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FeedRepository feedRepository;

    @MockitoBean
    private FeedCrawlerService crawlerService;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(new User("tx-test@marginalia.local", "Tx Tester", null));
        testCategory = categoryRepository.save(new Category(testUser, "Tech", 1));
    }

    @AfterEach
    void tearDown() {
        feedRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testAddFeedPersistsFeedWithoutCrawling() {
        FeedRequest request = new FeedRequest("https://example.com/rss.xml", "Test Feed", testCategory.getId());
        FeedResponse response = feedService.addFeed(request, testUser);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.feedUrl()).isEqualTo("https://example.com/rss.xml");
        assertThat(response.title()).isEqualTo("Test Feed");
        assertThat(response.categoryName()).isEqualTo("Tech");
        // Crawler metadata remains null because addFeed does not perform any crawling
        assertThat(response.siteUrl()).isNull();
        assertThat(response.description()).isNull();

        // Verify entity persisted in DB
        Feed savedFeed = feedRepository.findById(response.id()).orElseThrow();
        assertThat(savedFeed.getFeedUrl()).isEqualTo("https://example.com/rss.xml");
    }

    @Test
    void testAddFeedDuplicateThrowsException() {
        FeedRequest request = new FeedRequest("https://example.com/rss.xml", "Test Feed", testCategory.getId());
        feedService.addFeed(request, testUser);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            feedService.addFeed(request, testUser);
        });
    }

    @Test
    void testAddFeedRejectsSsrfUrls() {
        FeedRequest ssrfRequest = new FeedRequest("http://169.254.169.254/latest/meta-data", "SSRF", null);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            feedService.addFeed(ssrfRequest, testUser);
        });
    }
}
