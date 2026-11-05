package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.dto.FeedRequest;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest
@Transactional
class FeedCrawlerServiceTest {

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedService feedService;

    @Autowired
    private ArticleService articleService;

    private MockRestServiceServer mockServer;
    private FeedCrawlerService crawlerService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(new User("crawler-test@marginalia.local", "Crawler Tester", null));

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        crawlerService = new FeedCrawlerService(feedRepository, articleRepository, builder.build());
    }

    @Test
    void testCrawlRssFeedSuccessfully() {
        Feed feed = new Feed(testUser, null, "https://example.com/rss.xml", null, "Initial Title", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/rss.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/sample-rss.xml"),
                        MediaType.APPLICATION_XML
                ).header(HttpHeaders.ETAG, "\"etag-123\""));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);
        assertThat(result.totalEntries()).isEqualTo(2);
        assertThat(result.notModified()).isFalse();

        // Verify articles in DB
        List<Article> articles = articleRepository.findAll();
        assertThat(articles).hasSize(2);
        assertThat(articles).extracting(Article::getTitle)
                .containsExactlyInAnyOrder("First RSS Article", "Second RSS Article");
        assertThat(articles).extracting(Article::getGuid)
                .containsExactlyInAnyOrder("urn:uuid:rss-article-1", "urn:uuid:rss-article-2");

        // Verify feed was updated with etag and metadata
        Feed updatedFeed = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updatedFeed.getEtag()).isEqualTo("\"etag-123\"");
        assertThat(updatedFeed.getSiteUrl()).isEqualTo("https://example.com");
        assertThat(updatedFeed.getDescription()).isEqualTo("A test RSS feed for Marginalia");
        assertThat(updatedFeed.getLastFetchedAt()).isNotNull();
        assertThat(updatedFeed.getFetchErrorCount()).isEqualTo(0);

        mockServer.verify();
    }

    @Test
    void testCrawlAtomFeedSuccessfully() {
        Feed feed = new Feed(testUser, null, "https://example.org/atom.xml", null, "Initial Atom", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.org/atom.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/sample-atom.xml"),
                        MediaType.APPLICATION_ATOM_XML
                ));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(1);
        assertThat(result.totalEntries()).isEqualTo(1);

        List<Article> articles = articleRepository.findAll();
        assertThat(articles).hasSize(1);
        Article atomArticle = articles.getFirst();
        assertThat(atomArticle.getTitle()).isEqualTo("Atom Entry One");
        assertThat(atomArticle.getGuid()).isEqualTo("urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a");
        assertThat(atomArticle.getAuthor()).isEqualTo("Carol");
        assertThat(atomArticle.getContent()).contains("Full HTML content");

        mockServer.verify();
    }

    @Test
    void testConditionalHttpReturns304NotModified() {
        Feed feed = new Feed(testUser, null, "https://example.com/cached.xml", null, "Cached Feed", null);
        feed.setEtag("\"etag-abc\"");
        feed.setLastModifiedHeader("Mon, 01 Sep 2025 12:00:00 GMT");
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/cached.xml"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("If-None-Match", "\"etag-abc\""))
                .andExpect(header("If-Modified-Since", "Mon, 01 Sep 2025 12:00:00 GMT"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.notModified()).isTrue();
        assertThat(result.newArticles()).isEqualTo(0);
        assertThat(articleRepository.findAll()).isEmpty();

        mockServer.verify();
    }

    @Test
    void testArticleDeduplicationOnSubsequentCrawl() {
        Feed feed = new Feed(testUser, null, "https://example.com/rss.xml", null, "Feed", null);
        feed = feedRepository.save(feed);

        // Expect two requests to the same URL
        mockServer.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo("https://example.com/rss.xml"))
                .andRespond(withSuccess(new ClassPathResource("fixtures/sample-rss.xml"), MediaType.APPLICATION_XML));

        // First crawl: adds 2 articles
        CrawlResult firstResult = crawlerService.crawlFeed(feed);
        assertThat(firstResult.newArticles()).isEqualTo(2);

        // Second crawl: 0 new articles (already deduplicated by guid)
        CrawlResult secondResult = crawlerService.crawlFeed(feed);
        assertThat(secondResult.newArticles()).isEqualTo(0);
        assertThat(secondResult.totalEntries()).isEqualTo(2);

        assertThat(articleRepository.findAll()).hasSize(2);
        mockServer.verify();
    }

    @Test
    void testCrawlerHandlesErrorGracefully() {
        Feed feed = new Feed(testUser, null, "https://example.com/broken.xml", null, "Broken Feed", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/broken.xml"))
                .andRespond(withServerError());

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).isNotNull();

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).isNotNull();

        mockServer.verify();
    }

    @Test
    void testCrawlFeedByIdSuccessfully() {
        Feed feed = new Feed(testUser, null, "https://example.com/rss.xml", null, "Feed By Id", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/rss.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/sample-rss.xml"),
                        MediaType.APPLICATION_XML
                ));

        CrawlResult result = crawlerService.crawlFeed(feed.getId());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);
        mockServer.verify();
    }

    @Test
    void testExtractImageFromMediaRssAndHtmlImg() {
        String mediaRssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                <channel>
                    <title>Media Test Feed</title>
                    <link>https://example.com</link>
                    <description>Feed with media elements</description>
                    <item>
                        <title>Media RSS Article</title>
                        <link>https://example.com/media-1</link>
                        <guid>media-guid-1</guid>
                        <description>Summary with text</description>
                        <media:content url="https://cdn.example.com/media-thumb.jpg" medium="image" width="800" height="600"/>
                    </item>
                    <item>
                        <title>HTML Img Article</title>
                        <link>https://example.com/media-2</link>
                        <guid>media-guid-2</guid>
                        <description><![CDATA[<p><img src="https://cdn.example.com/embedded.png" alt="preview" /> Story description</p>]]></description>
                    </item>
                </channel>
            </rss>
        """;

        Feed feed = new Feed(testUser, null, "https://example.com/media.xml", null, "Media Feed", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/media.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mediaRssXml, MediaType.APPLICATION_XML));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);

        Article mediaArticle = articleRepository.findByFeedIdAndGuid(feed.getId(), "media-guid-1").orElseThrow();
        assertThat(mediaArticle.getImageUrl()).isEqualTo("https://cdn.example.com/media-thumb.jpg");

        Article imgArticle = articleRepository.findByFeedIdAndGuid(feed.getId(), "media-guid-2").orElseThrow();
        assertThat(imgArticle.getImageUrl()).isEqualTo("https://cdn.example.com/embedded.png");

        mockServer.verify();
    }

    @Test
    void testCrawlerRejectsSsrfFeedUrl() {
        Feed feed = new Feed(testUser, null, "http://169.254.169.254/latest/meta-data", null, "Metadata Feed", null);
        feed = feedRepository.save(feed);

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("blocked");

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).contains("blocked");
    }
    @Test
    void testCrawlerRejectsDoctypesAndXxe() {
        String xxeXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE rss [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <rss version="2.0">
                <channel>
                    <title>XXE Feed &xxe;</title>
                    <link>https://example.com</link>
                    <description>Feed with XXE</description>
                    <item>
                        <title>Article with XXE &xxe;</title>
                        <link>https://example.com/item1</link>
                        <guid>guid-xxe-1</guid>
                    </item>
                </channel>
            </rss>
        """;

        Feed feed = new Feed(testUser, null, "https://example.com/xxe.xml", null, "XXE Feed", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/xxe.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(xxeXml, MediaType.APPLICATION_XML));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).isNotNull();

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(articleRepository.findByFeedIdAndGuid(feed.getId(), "guid-xxe-1")).isEmpty();
        mockServer.verify();
    }

    @Test
    void testCrawlerFollowsValidHttpRedirect() {
        Feed feed = new Feed(testUser, null, "https://example.com/redirect-source.xml", null, "Redirect Feed", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/redirect-source.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "https://example.com/redirect-target.xml"));

        mockServer.expect(requestTo("https://example.com/redirect-target.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new ClassPathResource("fixtures/sample-rss.xml"), MediaType.APPLICATION_XML));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);
        mockServer.verify();
    }

    @Test
    void testCrawlerBlocksSsrfViaRedirectToPrivateIp() {
        Feed feed = new Feed(testUser, null, "https://example.com/safe-looking-feed.xml", null, "SSRF Redirect Feed", null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/safe-looking-feed.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "http://169.254.169.254/latest/meta-data"));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("blocked");
        assertThat(result.error()).contains("169.254.169.254");

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).contains("169.254.169.254");

        mockServer.verify();
    }

    @Test
    void testCrawlerBlocksRedirectLoop() {
        Feed feed = new Feed(testUser, null, "https://example.com/redirect-0.xml", null, "Loop Feed", null);
        feed = feedRepository.save(feed);

        for (int i = 0; i <= 5; i++) {
            mockServer.expect(requestTo("https://example.com/redirect-" + i + ".xml"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, "https://example.com/redirect-" + (i + 1) + ".xml"));
        }

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("Too many redirects");

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).contains("Too many redirects");

        mockServer.verify();
    }

    @Test
    void testCrawlerRejectsFeedExceedingContentLengthHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer customMockServer = MockRestServiceServer.bindTo(builder).build();
        FeedCrawlerService sizeLimitedCrawler = new FeedCrawlerService(
                feedRepository, articleRepository, builder.build(), DataSize.ofBytes(100));

        Feed feed = new Feed(testUser, null, "https://example.com/oversized-header.xml", null, "Oversized Header Feed", null);
        feed = feedRepository.save(feed);

        customMockServer.expect(requestTo("https://example.com/oversized-header.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<rss></rss>", MediaType.APPLICATION_XML)
                        .header(HttpHeaders.CONTENT_LENGTH, "5000"));

        CrawlResult result = sizeLimitedCrawler.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("exceeds maximum allowed size");

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).contains("exceeds maximum allowed size");

        customMockServer.verify();
    }

    @Test
    void testCrawlerRejectsFeedExceedingMaxBodySizeDuringStreaming() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer customMockServer = MockRestServiceServer.bindTo(builder).build();
        FeedCrawlerService sizeLimitedCrawler = new FeedCrawlerService(
                feedRepository, articleRepository, builder.build(), DataSize.ofBytes(50));

        Feed feed = new Feed(testUser, null, "https://example.com/oversized-body.xml", null, "Oversized Body Feed", null);
        feed = feedRepository.save(feed);

        // Generates ~200 bytes of XML without Content-Length header
        String largeBody = "<rss version=\"2.0\"><channel><title>Very Long Feed Content That Exceeds The Limit</title></channel></rss>";

        customMockServer.expect(requestTo("https://example.com/oversized-body.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(largeBody, MediaType.APPLICATION_XML));

        CrawlResult result = sizeLimitedCrawler.crawlFeed(feed);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("exceeds maximum allowed size");

        Feed updated = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updated.getFetchErrorCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).contains("exceeds maximum allowed size");

        customMockServer.verify();
    }

    @Test
    void testCrawlRssWithNestedAnchorTitlesAndAutoDetectFeedTitle() {
        String fiPlanningXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0" xml:base="https://www.fiplanning.com/">
              <channel>
                <title>FI Planning - アメリカ生活とパーソナルファイナンスの情報シェアサイト</title>
                <link>https://www.fiplanning.com/</link>
                <description/>
                <language>ja</language>
                <item>
                  <title><a href="/forum/20261028-%E7%AC%AC%E4%B8%89%E5%9B%BD" hreflang="ja">第三国での銀行口座について</a></title>
                  <link>https://www.fiplanning.com/%3Ca%20href%3D%22/forum/20261028-%25E7%25AC%25AC%25E4%25B8%2589%25E5%259B%25BD%22%20hreflang%3D%22ja%22%3E%E7%B6%9A%E3%81%8D%E3%82%92%E8%AA%AD%E3%82%80%3C/a%3E</link>
                  <description>&lt;p&gt;こんにちは。&lt;/p&gt;</description>
                  <pubDate>2026-10-28T08:14:38-0400</pubDate>
                  <dc:creator>
                    <span class="username" style="color: #800000 "> ころん（ゲスト）</span>
                  </dc:creator>
                  <guid isPermaLink="true">https://www.fiplanning.com/%3Ca%20href%3D%22/forum/20261028-%25E7%25AC%25AC%25E4%25B8%2589%25E5%259B%25BD%22</guid>
                </item>
                <item>
                  <title><a href="/forum/20260611-wealth-plan" hreflang="ja">wealth plan について</a></title>
                  <link>https://www.fiplanning.com/%3Ca%20href%3D%22/forum/20260611-wealth-plan%22%20hreflang%3D%22ja%22%3E%E7%B6%9A%E3%81%8D%E3%82%92%E8%AA%AD%E3%82%80%3C/a%3E</link>
                  <description>&lt;p&gt;相談です。&lt;/p&gt;</description>
                  <pubDate>2026-06-11T10:38:08-0400</pubDate>
                  <dc:creator>
                    <span class="username"> テスト（ゲスト）</span>
                  </dc:creator>
                  <guid isPermaLink="true">https://www.fiplanning.com/%3Ca%20href%3D%22/forum/20260611-wealth-plan%22</guid>
                </item>
              </channel>
            </rss>
            """;

        // Feed created without custom title (defaults to feedUrl)
        String feedUrl = "https://www.fiplanning.com/rss.xml";
        Feed feed = new Feed(testUser, null, feedUrl, null, feedUrl, null);
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo(feedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fiPlanningXml, MediaType.APPLICATION_XML));

        CrawlResult result = crawlerService.crawlFeed(feed);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);

        // Feed title should be auto-detected from <channel><title>
        Feed updatedFeed = feedRepository.findById(feed.getId()).orElseThrow();
        assertThat(updatedFeed.getTitle())
                .isEqualTo("FI Planning - アメリカ生活とパーソナルファイナンスの情報シェアサイト");
        assertThat(updatedFeed.getSiteUrl()).isEqualTo("https://www.fiplanning.com/");

        // Each article should have its own <item><title>, clean author, and resolved article URL
        List<Article> articles = articleRepository.findAll();
        assertThat(articles).hasSize(2);
        assertThat(articles).extracting(Article::getTitle)
                .containsExactlyInAnyOrder("第三国での銀行口座について", "wealth plan について");
        assertThat(articles).extracting(Article::getAuthor)
                .containsExactlyInAnyOrder("ころん（ゲスト）", "テスト（ゲスト）");
        assertThat(articles).extracting(Article::getArticleUrl)
                .containsExactlyInAnyOrder(
                        "https://www.fiplanning.com/forum/20261028-%E7%AC%AC%E4%B8%89%E5%9B%BD",
                        "https://www.fiplanning.com/forum/20260611-wealth-plan"
                );

        mockServer.verify();
    }

    @Test
    void testFeedFilterKeywordsExcludeMatchingArticlesOnCrawlAndUpdate() {
        Feed feed = new Feed(testUser, null, "https://example.com/rss.xml", null, "Filter Test Feed", null);
        feed.setFilterKeywordList(List.of("Second"));
        feed = feedRepository.save(feed);

        mockServer.expect(requestTo("https://example.com/rss.xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        new ClassPathResource("fixtures/sample-rss.xml"),
                        MediaType.APPLICATION_XML
                ));

        CrawlResult result = crawlerService.crawlFeed(feed);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newArticles()).isEqualTo(2);

        // Only "First RSS Article" should appear in stream and unread counts; "Second RSS Article" is filtered
        var unreadPage = articleService.getUnreadArticlesByFeed(feed.getId(), testUser.getId(), PageRequest.of(0, 20));
        assertThat(unreadPage.getContent()).extracting(a -> a.title()).containsExactly("First RSS Article");
        assertThat(articleService.getUnreadCount(testUser.getId())).isEqualTo(1L);

        FeedResponse feedDto = feedService.getFeed(feed.getId(), testUser.getId());
        assertThat(feedDto.filteredCount()).isEqualTo(1L);
        assertThat(feedDto.filterKeywords()).containsExactly("Second");

        // Mark "First RSS Article" as read, then update filter keywords to ["First", "Second"]
        Long firstArticleId = unreadPage.getContent().getFirst().id();
        articleService.markAsRead(testUser.getId(), firstArticleId, true);

        FeedResponse afterAddFilter = feedService.updateFeed(
                feed.getId(),
                new FeedRequest(feed.getFeedUrl(), feed.getTitle(), null, List.of("First", "Second")),
                testUser.getId()
        );
        // Option A: already-read "First RSS Article" is not retroactively filtered; only "Second" remains filtered
        assertThat(afterAddFilter.filteredCount()).isEqualTo(1L);

        // Now clear filter keywords -> "Second RSS Article" (which was filtered) is restored to unread stream
        FeedResponse afterClearFilter = feedService.updateFeed(
                feed.getId(),
                new FeedRequest(feed.getFeedUrl(), feed.getTitle(), null, List.of()),
                testUser.getId()
        );
        assertThat(afterClearFilter.filteredCount()).isEqualTo(0L);
        var restoredUnread = articleService.getUnreadArticlesByFeed(feed.getId(), testUser.getId(), PageRequest.of(0, 20));
        assertThat(restoredUnread.getContent()).extracting(a -> a.title()).containsExactly("Second RSS Article");

        mockServer.verify();
    }
}
