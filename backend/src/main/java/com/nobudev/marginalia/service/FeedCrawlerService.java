package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Core feed crawling engine. Fetches RSS/Atom feeds using conditional HTTP
 * (ETag / If-Modified-Since) to minimize bandwidth, parses entries with ROME,
 * and persists new articles.
 */
@Service
public class FeedCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(FeedCrawlerService.class);

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final RestClient restClient;

    public FeedCrawlerService(FeedRepository feedRepository,
                              ArticleRepository articleRepository,
                              RestClient restClient) {
        this.feedRepository = feedRepository;
        this.articleRepository = articleRepository;
        this.restClient = restClient;
    }

    /**
     * Scheduled task that crawls all feeds. Runs with a configurable fixed delay
     * (default 15 minutes). Each feed is crawled independently so one failure
     * does not block others.
     */
    @Scheduled(fixedDelayString = "${app.crawler.interval}")
    public void crawlAllFeeds() {
        List<Feed> feeds = feedRepository.findAll();
        if (feeds.isEmpty()) {
            log.debug("No feeds to crawl");
            return;
        }

        log.info("Starting scheduled crawl of {} feeds", feeds.size());
        int totalNew = 0;
        int errors = 0;

        for (Feed feed : feeds) {
            CrawlResult result = crawlFeed(feed);
            totalNew += result.newArticles();
            if (!result.isSuccess()) {
                errors++;
            }
        }

        log.info("Feed crawl complete: {} feeds processed, {} new articles, {} errors",
                feeds.size(), totalNew, errors);
    }

    /**
     * Crawls a single feed by ID. Returns a CrawlResult summarizing what happened.
     */
    public CrawlResult crawlFeed(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));
        return crawlFeed(feed);
    }

    /**
     * Crawl a single feed. Sends conditional HTTP headers (ETag / If-Modified-Since)
     * to avoid re-downloading unchanged content. Returns a CrawlResult summarizing
     * what happened.
     */
    public CrawlResult crawlFeed(Feed feed) {
        log.debug("Crawling feed: {} ({})", feed.getTitle(), feed.getFeedUrl());
        try {
            FetchResult fetchResult = fetchFeed(feed);

            if (fetchResult.statusCode() == 304) {
                log.debug("Feed not modified (304): {}", feed.getTitle());
                feed.setLastFetchedAt(LocalDateTime.now());
                feed.setFetchErrorCount(0);
                feed.setLastErrorMessage(null);
                feedRepository.save(feed);
                return new CrawlResult(0, 0, true, null);
            }

            if (fetchResult.body() == null || fetchResult.body().length == 0) {
                throw new RuntimeException("Empty response body from feed: " + feed.getFeedUrl());
            }

            // Parse with ROME
            SyndFeedInput input = new SyndFeedInput();
            input.setAllowDoctypes(true);
            SyndFeed syndFeed;
            try (XmlReader xmlReader = new XmlReader(new ByteArrayInputStream(fetchResult.body()))) {
                syndFeed = input.build(xmlReader);
            }

            // Update feed metadata from the parsed feed
            updateFeedMetadata(feed, syndFeed);

            // Update conditional HTTP headers for next crawl
            updateConditionalHeaders(feed, fetchResult.headers());

            feed.setLastFetchedAt(LocalDateTime.now());
            feed.setFetchErrorCount(0);
            feed.setLastErrorMessage(null);

            // Process entries and save new articles
            int newCount = 0;
            List<SyndEntry> entries = syndFeed.getEntries();
            for (SyndEntry entry : entries) {
                if (processEntry(feed, entry)) {
                    newCount++;
                }
            }

            feedRepository.save(feed);
            log.info("Crawled '{}': {} new / {} total entries", feed.getTitle(), newCount, entries.size());
            return new CrawlResult(newCount, entries.size(), false, null);

        } catch (Exception e) {
            log.error("Error crawling feed '{}' ({}): {}", feed.getTitle(), feed.getFeedUrl(), e.getMessage());
            feed.setFetchErrorCount(feed.getFetchErrorCount() + 1);
            feed.setLastErrorMessage(truncate(e.getMessage(), 1000));
            feed.setLastFetchedAt(LocalDateTime.now());
            feedRepository.save(feed);
            return new CrawlResult(0, 0, false, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private record FetchResult(int statusCode, HttpHeaders headers, byte[] body) {}

    /**
     * Fetches the feed URL, sending conditional HTTP headers if available.
     * Uses RestClient.exchange() for full control over the response handling,
     * including 304 Not Modified which has no response body.
     */
    private FetchResult fetchFeed(Feed feed) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(feed.getFeedUrl());

        if (feed.getEtag() != null && !feed.getEtag().isEmpty()) {
            spec = spec.header("If-None-Match", feed.getEtag());
        }
        if (feed.getLastModifiedHeader() != null && !feed.getLastModifiedHeader().isEmpty()) {
            spec = spec.header("If-Modified-Since", feed.getLastModifiedHeader());
        }

        return spec.exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            HttpHeaders headers = response.getHeaders();
            byte[] body = (status.value() == 304)
                    ? new byte[0]
                    : response.getBody().readAllBytes();
            return new FetchResult(status.value(), headers, body);
        });
    }

    private void updateFeedMetadata(Feed feed, SyndFeed syndFeed) {
        if (syndFeed.getLink() != null && !syndFeed.getLink().isBlank()) {
            feed.setSiteUrl(syndFeed.getLink());
        }
        if (syndFeed.getDescription() != null && !syndFeed.getDescription().isBlank()) {
            feed.setDescription(syndFeed.getDescription());
        }
        if (syndFeed.getImage() != null && syndFeed.getImage().getUrl() != null) {
            feed.setIconUrl(syndFeed.getImage().getUrl());
        }
    }

    private void updateConditionalHeaders(Feed feed, HttpHeaders headers) {
        String etag = headers.getETag();
        if (etag != null) {
            feed.setEtag(etag);
        }
        List<String> lastModified = headers.getValuesAsList("Last-Modified");
        if (!lastModified.isEmpty()) {
            feed.setLastModifiedHeader(lastModified.getFirst());
        }
    }

    /**
     * Processes a single feed entry. Returns true if a new article was saved,
     * false if it already existed (deduplicated by feed_id + guid).
     */
    private boolean processEntry(Feed feed, SyndEntry entry) {
        String guid = extractGuid(entry);

        if (articleRepository.existsByFeedIdAndGuid(feed.getId(), guid)) {
            return false;
        }

        Article article = new Article();
        article.setFeed(feed);
        article.setGuid(guid);
        article.setTitle(truncate(
                entry.getTitle() != null ? entry.getTitle() : "Untitled", 512));
        article.setAuthor(truncate(entry.getAuthor(), 255));
        article.setArticleUrl(
                entry.getLink() != null ? entry.getLink() : feed.getSiteUrl());
        article.setPublishedAt(extractPublishedDate(entry));

        // Extract full content (prefer content over description)
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            article.setContent(entry.getContents().getFirst().getValue());
        }

        // Extract summary / description
        if (entry.getDescription() != null) {
            article.setSummary(entry.getDescription().getValue());
        }

        // If no content was found, fall back to summary
        if (article.getContent() == null && article.getSummary() != null) {
            article.setContent(article.getSummary());
        }

        // Extract image from enclosures (common in media-rich feeds)
        if (entry.getEnclosures() != null) {
            for (var enclosure : entry.getEnclosures()) {
                if (enclosure.getType() != null && enclosure.getType().startsWith("image/")) {
                    article.setImageUrl(enclosure.getUrl());
                    break;
                }
            }
        }

        articleRepository.save(article);
        return true;
    }

    /**
     * Extracts a unique identifier for the entry, preferring the RSS guid/Atom id,
     * falling back to the entry link, and finally a hash-based fallback.
     */
    private String extractGuid(SyndEntry entry) {
        if (entry.getUri() != null && !entry.getUri().isBlank()) {
            return truncate(entry.getUri(), 255);
        }
        if (entry.getLink() != null && !entry.getLink().isBlank()) {
            return truncate(entry.getLink(), 255);
        }
        // Last resort: hash of title + date
        String fallback = (entry.getTitle() != null ? entry.getTitle() : "")
                + (entry.getPublishedDate() != null ? entry.getPublishedDate().toString() : "");
        return truncate("hash:" + Integer.toHexString(fallback.hashCode()), 255);
    }

    private LocalDateTime extractPublishedDate(SyndEntry entry) {
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate().toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDateTime();
        }
        if (entry.getUpdatedDate() != null) {
            return entry.getUpdatedDate().toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDateTime();
        }
        return LocalDateTime.now();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
