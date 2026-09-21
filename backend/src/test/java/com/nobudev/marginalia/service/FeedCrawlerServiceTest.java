package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
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
}
