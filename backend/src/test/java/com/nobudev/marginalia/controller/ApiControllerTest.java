package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.*;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import com.nobudev.marginalia.service.FeedCrawlerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiControllerTest {

    @Autowired
    private CategoryController categoryController;

    @Autowired
    private FeedController feedController;

    @Autowired
    private ArticleController articleController;

    @Autowired
    private OpmlController opmlController;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @MockitoBean
    private FeedCrawlerService crawlerService;

    private User testUser;
    private Category testCategory;
    private Feed testFeed;
    private Article testArticle;

    @BeforeEach
    void setUp() {
        when(crawlerService.crawlFeed(any(Feed.class))).thenReturn(new CrawlResult(0, 0, false, null));
        when(crawlerService.crawlFeed(any(Long.class))).thenReturn(new CrawlResult(0, 0, false, null));

        testUser = userRepository.save(new User("api-test@marginalia.local", "API Tester", null));

        org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                testUser.getEmail(), null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        testCategory = categoryRepository.save(new Category(testUser, "News", 1));
        testFeed = feedRepository.save(new Feed(testUser, testCategory, "https://example.com/feed.xml", "https://example.com", "Example News", "Desc"));
        testArticle = articleRepository.save(new Article(
                testFeed,
                "guid-101",
                "Article Title 101",
                "Writer",
                "<p>Story body</p>",
                "Story snippet",
                "https://example.com/101",
                null,
                LocalDateTime.now()
        ));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- Category Controller ---

    @Test
    void testGetCategories() {
        List<CategoryResponse> categories = categoryController.getCategories();
        assertThat(categories).isNotEmpty();
        assertThat(categories).extracting(CategoryResponse::name).contains("News");
    }

    @Test
    void testCreateCategory() {
        CategoryRequest request = new CategoryRequest("Science", 2);
        ResponseEntity<CategoryResponse> response = categoryController.createCategory(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Science");
        assertThat(response.getBody().sortOrder()).isEqualTo(2);
    }

    @Test
    void testUpdateCategory() {
        CategoryRequest request = new CategoryRequest("Tech News", 5);
        CategoryResponse response = categoryController.updateCategory(testCategory.getId(), request);

        assertThat(response.name()).isEqualTo("Tech News");
        assertThat(response.sortOrder()).isEqualTo(5);
    }

    @Test
    void testDeleteCategory() {
        ResponseEntity<Void> response = categoryController.deleteCategory(testCategory.getId());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(categoryRepository.findById(testCategory.getId())).isEmpty();
    }

    // --- Feed Controller ---

    @Test
    void testGetFeeds() {
        List<FeedResponse> feeds = feedController.getFeeds();
        assertThat(feeds).isNotEmpty();
        assertThat(feeds).extracting(FeedResponse::title).contains("Example News");
    }

    @Test
    void testGetSingleFeed() {
        FeedResponse feed = feedController.getFeed(testFeed.getId());
        assertThat(feed.title()).isEqualTo("Example News");
        assertThat(feed.feedUrl()).isEqualTo("https://example.com/feed.xml");
    }

    @Test
    void testAddFeed() {
        FeedRequest request = new FeedRequest("https://newsite.com/rss", "New Site", testCategory.getId());
        ResponseEntity<FeedResponse> response = feedController.addFeed(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().feedUrl()).isEqualTo("https://newsite.com/rss");
    }

    @Test
    void testDeleteFeed() {
        ResponseEntity<Void> response = feedController.deleteFeed(testFeed.getId());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(feedRepository.findById(testFeed.getId())).isEmpty();
    }

    @Test
    void testRefreshFeed() {
        CrawlResult result = feedController.refreshFeed(testFeed.getId());
        assertThat(result.isSuccess()).isTrue();
    }

    // --- Article Controller ---

    @Test
    void testGetArticles() {
        Page<ArticleResponse> articles = articleController.getArticles(
                null, null, false, PageRequest.of(0, 20));

        assertThat(articles.getContent()).isNotEmpty();
        ArticleResponse first = articles.getContent().getFirst();
        assertThat(first.title()).isEqualTo("Article Title 101");
        assertThat(first.isRead()).isFalse();
        assertThat(first.isSaved()).isFalse();
    }

    @Test
    void testMarkArticleReadAndSaved() {
        // Mark as read
        ResponseEntity<Void> readResp = articleController.markRead(testArticle.getId(), true);
        assertThat(readResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Toggle saved
        ResponseEntity<Void> saveResp = articleController.toggleSaved(testArticle.getId(), true);
        assertThat(saveResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Fetch article and verify state
        ArticleResponse article = articleController.getArticle(testArticle.getId());
        assertThat(article.isRead()).isTrue();
        assertThat(article.isSaved()).isTrue();

        // Verify it appears in saved stream (both unsorted and with publishedAt default sort)
        Page<ArticleResponse> savedArticles = articleController.getArticles(
                null, null, true, PageRequest.of(0, 20));
        assertThat(savedArticles.getContent()).hasSize(1);
        assertThat(savedArticles.getContent().getFirst().id()).isEqualTo(testArticle.getId());

        Page<ArticleResponse> savedWithDefaultSort = articleController.getArticles(
                null, null, true, PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "publishedAt")));
        assertThat(savedWithDefaultSort.getContent()).hasSize(1);
        assertThat(savedWithDefaultSort.getContent().getFirst().id()).isEqualTo(testArticle.getId());
    }

    @Test
    void testUpdateArticleStateViaPatch() {
        ArticleStateRequest patchRequest = new ArticleStateRequest(true, true);
        ResponseEntity<Void> response = articleController.updateArticleState(testArticle.getId(), patchRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArticleResponse article = articleController.getArticle(testArticle.getId());
        assertThat(article.isRead()).isTrue();
        assertThat(article.isSaved()).isTrue();
    }

    @Test
    void testMarkAllAsRead() {
        ResponseEntity<Void> response = articleController.markAllAsRead(List.of(testArticle.getId()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArticleResponse article = articleController.getArticle(testArticle.getId());
        assertThat(article.isRead()).isTrue();
    }

    @Test
    void testGetArticlesUnreadOnly() {
        // Initially testArticle is unread
        Page<ArticleResponse> unread = articleController.getArticles(null, null, false, true, PageRequest.of(0, 20));
        assertThat(unread.getContent()).hasSize(1);
        assertThat(unread.getContent().getFirst().id()).isEqualTo(testArticle.getId());

        // Mark as read
        articleController.markRead(testArticle.getId(), true);

        // Now unread query returns 0 items
        Page<ArticleResponse> afterRead = articleController.getArticles(null, null, false, true, PageRequest.of(0, 20));
        assertThat(afterRead.getContent()).isEmpty();
    }

    @Test
    void testMarkAllAsReadWithoutIds() {
        // Initially 1 unread
        assertThat(articleController.getUnreadCount().get("unreadCount")).isEqualTo(1L);

        // Mark all as read with null/empty list
        ResponseEntity<Void> response = articleController.markAllAsRead(null, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Now 0 unread
        assertThat(articleController.getUnreadCount().get("unreadCount")).isEqualTo(0L);
    }

    @Test
    void testGetUnreadCountIncludesArticlesWithNoStateRow() {
        // testArticle has no ArticleUserState row — it should count as unread
        Map<String, Long> count = articleController.getUnreadCount();
        assertThat(count.get("unreadCount")).isEqualTo(1L);
    }

    @Test
    void testGetUnreadCountsReturnsTotalAndByFeed() {
        Map<String, Object> counts = articleController.getUnreadCounts();
        assertThat(counts.get("total")).isEqualTo(1L);
        assertThat(counts.get("byFeed")).isNotNull();
    }

    @Test
    void testGetUnreadCountDecreasesAfterMarkingRead() {
        // Initially 1 unread article (testArticle, no state row)
        assertThat(articleController.getUnreadCount().get("unreadCount")).isEqualTo(1L);

        // Mark it as read
        articleController.markRead(testArticle.getId(), true);

        // Now 0 unread
        assertThat(articleController.getUnreadCount().get("unreadCount")).isEqualTo(0L);
    }

    // --- OPML Controller ---

    @Test
    void testOpmlExport() {
        ResponseEntity<String> response = opmlController.exportOpml();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<opml version=\"2.0\">");
        assertThat(response.getBody()).contains("Example News");
    }

    @Test
    void testOpmlImport() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        assertThat(is).isNotNull();
        MockMultipartFile file = new MockMultipartFile("file", "subscriptions.opml", "text/xml", is.readAllBytes());

        List<FeedResponse> imported = opmlController.importOpml(file);
        assertThat(imported).hasSize(4);
    }

    // --- Global Exception Handler ---

    @Test
    void testExceptionHandlerNotFound() {
        IllegalArgumentException ex = new IllegalArgumentException("Feed not found: 999");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleIllegalArgument(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Feed not found: 999");
    }

    @Test
    void testExceptionHandlerBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Feed already subscribed: https://example.com");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleIllegalArgument(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
