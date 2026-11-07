package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.exception.FeedSizeExceededException;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import com.nobudev.marginalia.util.UrlSafetyValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;

/**
 * Core feed crawling engine. Fetches RSS/Atom feeds using conditional HTTP
 * (ETag / If-Modified-Since) to minimize bandwidth, parses entries with ROME,
 * and persists new articles.
 */
@Service
public class FeedCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(FeedCrawlerService.class);

    private static final int MAX_OG_FETCHES_PER_CRAWL = 25;
    private static final int MAX_OG_HTML_BYTES = 65_536;
    private static final int MAX_OG_PROBED_CACHE_SIZE = 4_096;

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final RestClient restClient;
    private final RestClient openGraphRestClient;
    private final DataSize maxFeedSize;

    /**
     * Bounded LRU set of article URLs already probed for Open Graph metadata so
     * articles whose pages genuinely lack og:image are not re-fetched on subsequent crawls.
     */
    private final Set<String> ogProbedUrls = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_OG_PROBED_CACHE_SIZE;
                }
            })
    );

    public FeedCrawlerService(FeedRepository feedRepository,
                              ArticleRepository articleRepository,
                              RestClient restClient) {
        this(feedRepository, articleRepository, restClient, null, DataSize.ofMegabytes(10));
    }

    public FeedCrawlerService(FeedRepository feedRepository,
                              ArticleRepository articleRepository,
                              RestClient restClient,
                              DataSize maxFeedSize) {
        this(feedRepository, articleRepository, restClient, null, maxFeedSize);
    }

    @Autowired
    public FeedCrawlerService(FeedRepository feedRepository,
                              ArticleRepository articleRepository,
                              RestClient restClient,
                              @Qualifier("openGraphRestClient") RestClient openGraphRestClient,
                              @Value("${app.crawler.max-feed-size:10MB}") DataSize maxFeedSize) {
        this.feedRepository = feedRepository;
        this.articleRepository = articleRepository;
        this.restClient = restClient;
        this.openGraphRestClient = openGraphRestClient;
        this.maxFeedSize = maxFeedSize != null ? maxFeedSize : DataSize.ofMegabytes(10);
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
        int[] ogBudget = {MAX_OG_FETCHES_PER_CRAWL};
        try {
            FetchResult fetchResult = fetchFeed(feed);

            if (fetchResult.statusCode() == 304) {
                log.debug("Feed not modified (304): {}", feed.getTitle());
                feed.setLastFetchedAt(LocalDateTime.now());
                feed.setFetchErrorCount(0);
                feed.setLastErrorMessage(null);
                feedRepository.save(feed);
                backfillMissingOpenGraphImages(feed, ogBudget);
                return new CrawlResult(0, 0, true, null);
            }

            if (fetchResult.body() == null || fetchResult.body().length == 0) {
                throw new RuntimeException("Empty response body from feed: " + feed.getFeedUrl());
            }

            // Parse with ROME (pre-normalizing nested HTML in <title>/<dc:creator>/<link>)
            SyndFeed syndFeed = parseSyndFeed(fetchResult.body(), feed.getFeedUrl());

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
                if (processEntry(feed, entry, ogBudget)) {
                    newCount++;
                }
            }

            backfillMissingOpenGraphImages(feed, ogBudget);

            feedRepository.save(feed);
            log.info("Crawled '{}': {} new / {} total entries", feed.getTitle(), newCount, entries.size());
            return new CrawlResult(newCount, entries.size(), false, null);

        } catch (FeedSizeExceededException e) {
            log.warn("Feed '{}' ({}) exceeded maximum allowed size: {}", feed.getTitle(), feed.getFeedUrl(), e.getMessage());
            feed.setFetchErrorCount(feed.getFetchErrorCount() + 1);
            feed.setLastErrorMessage(truncate(e.getMessage(), 1000));
            feed.setLastFetchedAt(LocalDateTime.now());
            feedRepository.save(feed);
            return new CrawlResult(0, 0, false, e.getMessage());
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

    private static final int MAX_REDIRECTS = 5;

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private record FetchResult(int statusCode, HttpHeaders headers, byte[] body) {}

    /**
     * Fetches the feed URL, sending conditional HTTP headers if available.
     * Manually follows HTTP redirects up to MAX_REDIRECTS, re-validating each
     * destination URL with UrlSafetyValidator to prevent SSRF bypasses via redirects.
     * Uses RestClient.exchange() for full control over the response handling,
     * including 304 Not Modified which has no response body.
     */
    private FetchResult fetchFeed(Feed feed) {
        String currentUrl = feed.getFeedUrl();

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            UrlSafetyValidator.validate(currentUrl);

            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(currentUrl);

            if (hop == 0) {
                if (feed.getEtag() != null && !feed.getEtag().isEmpty()) {
                    spec = spec.header("If-None-Match", feed.getEtag());
                }
                if (feed.getLastModifiedHeader() != null && !feed.getLastModifiedHeader().isEmpty()) {
                    spec = spec.header("If-Modified-Since", feed.getLastModifiedHeader());
                }
            }

            final String requestUrl = currentUrl;
            FetchResult fetchResult = spec.exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                HttpHeaders headers = response.getHeaders();
                byte[] body;
                if (status.value() == 304 || isRedirect(status.value())) {
                    body = new byte[0];
                } else {
                    long contentLength = headers.getContentLength();
                    long maxBytes = maxFeedSize.toBytes();
                    if (contentLength > 0 && contentLength > maxBytes) {
                        throw new FeedSizeExceededException(requestUrl, maxBytes, contentLength);
                    }
                    body = readBoundedBody(response.getBody(), maxBytes, requestUrl);
                }
                return new FetchResult(status.value(), headers, body);
            });

            if (isRedirect(fetchResult.statusCode())) {
                if (hop == MAX_REDIRECTS) {
                    throw new IllegalArgumentException("Too many redirects (max " + MAX_REDIRECTS + ") for feed: " + feed.getFeedUrl());
                }
                String location = fetchResult.headers().getFirst("Location");
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("Redirect response missing Location header from URL: " + currentUrl);
                }
                try {
                    currentUrl = URI.create(currentUrl).resolve(location.trim()).toString();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid redirect Location header: " + location, e);
                }
                log.debug("Following redirect for feed '{}': {} -> {}", feed.getTitle(), feed.getFeedUrl(), currentUrl);
                continue;
            }

            return fetchResult;
        }

        throw new IllegalArgumentException("Too many redirects for feed: " + feed.getFeedUrl());
    }

    private byte[] readBoundedBody(InputStream in, long maxBytes, String url) throws IOException {
        int limit = (int) Math.min(maxBytes, Integer.MAX_VALUE - 1);
        byte[] buffer = in.readNBytes(limit + 1);
        if (buffer.length > limit) {
            throw new FeedSizeExceededException(url, maxBytes, null);
        }
        return buffer;
    }

    private static final Pattern ANCHOR_HREF_PATTERN = Pattern.compile(
            "<a\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private static final DateTimeFormatter FLEXIBLE_ISO_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .toFormatter(Locale.ROOT);

    /**
     * Parses raw feed XML bytes into a ROME SyndFeed while enforcing XXE/DOCTYPE
     * protections and normalizing non-standard CMS/Drupal RSS constructs where
     * elements like {@code <title>} or {@code <dc:creator>} wrap their text inside
     * nested HTML elements ({@code <a>}, {@code <span>}) or URL-encode {@code <a>}
     * tags inside {@code <link>}.
     */
    private SyndFeed parseSyndFeed(byte[] body, String feedUrl) throws Exception {
        SAXBuilder saxBuilder = new SAXBuilder(XMLReaders.NONVALIDATING);
        saxBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        saxBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        saxBuilder.setExpandEntities(false);

        Document document;
        try (XmlReader xmlReader = new XmlReader(new ByteArrayInputStream(body));
             PushbackReader reader = new PushbackReader(xmlReader, 1)) {
            int ch;
            while ((ch = reader.read()) != -1) {
                if (!Character.isWhitespace(ch) && ch != '\uFEFF') {
                    reader.unread(ch);
                    break;
                }
            }
            document = saxBuilder.build(reader);
        }

        normalizeFeedDocument(document, feedUrl);

        SyndFeedInput input = new SyndFeedInput();
        input.setAllowDoctypes(false);
        return input.build(document);
    }

    private void normalizeFeedDocument(Document document, String fallbackBaseUrl) {
        if (document == null || !document.hasRootElement()) {
            return;
        }
        Element root = document.getRootElement();
        String baseUrl = root.getAttributeValue("base", Namespace.XML_NAMESPACE);

        Element channel = root.getChild("channel", root.getNamespace());
        if (channel == null) {
            channel = root.getChild("channel");
        }

        if ((baseUrl == null || baseUrl.isBlank()) && channel != null) {
            Element channelLink = channel.getChild("link", channel.getNamespace());
            if (channelLink == null) {
                channelLink = channel.getChild("link");
            }
            if (channelLink != null && !channelLink.getValue().isBlank()) {
                baseUrl = channelLink.getValue().trim();
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = fallbackBaseUrl;
        }

        // Normalize channel/feed-level <title> if it wraps text in child elements
        normalizeTitleElement(channel != null ? findChildByLocalName(channel, "title") : findChildByLocalName(root, "title"));

        List<Element> items = new ArrayList<>();
        if (channel != null) {
            for (Element child : channel.getChildren()) {
                if ("item".equalsIgnoreCase(child.getName())) {
                    items.add(child);
                }
            }
        }
        for (Element child : root.getChildren()) {
            if ("item".equalsIgnoreCase(child.getName()) || "entry".equalsIgnoreCase(child.getName())) {
                items.add(child);
            }
        }

        for (Element item : items) {
            String extractedHref = normalizeTitleElement(findChildByLocalName(item, "title"));
            normalizeLinkElement(findChildByLocalName(item, "link"), extractedHref, baseUrl);
            normalizeAuthorElements(item);
            normalizePubDateElement(findChildByLocalName(item, "pubDate"));
        }
    }

    private Element findChildByLocalName(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (Element child : parent.getChildren()) {
            if (localName.equalsIgnoreCase(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Flattens nested XML/HTML tags (such as {@code <a href="...">Title</a>}) inside a
     * {@code <title>} element into direct text so ROME's {@code Element.getText()} reads
     * the full title. Returns any {@code href} discovered inside a nested {@code <a>}.
     */
    private String normalizeTitleElement(Element titleEl) {
        if (titleEl == null) {
            return null;
        }
        String extractedHref = findFirstAnchorHref(titleEl);
        if (!titleEl.getChildren().isEmpty()) {
            String fullText = titleEl.getValue().trim();
            titleEl.setText(fullText);
        } else {
            String rawText = titleEl.getText();
            if (rawText != null && rawText.contains("<")) {
                if (extractedHref == null) {
                    Matcher m = ANCHOR_HREF_PATTERN.matcher(rawText);
                    if (m.find()) {
                        extractedHref = m.group(1).trim();
                    }
                }
                if (ANCHOR_HREF_PATTERN.matcher(rawText).find()) {
                    String stripped = HTML_TAG_PATTERN.matcher(rawText).replaceAll("").trim();
                    if (!stripped.isEmpty()) {
                        titleEl.setText(stripped);
                    }
                }
            }
        }
        return extractedHref;
    }

    private String findFirstAnchorHref(Element el) {
        if ("a".equalsIgnoreCase(el.getName())) {
            String href = el.getAttributeValue("href");
            if (href != null && !href.isBlank()) {
                return href.trim();
            }
        }
        for (Element child : el.getChildren()) {
            String href = findFirstAnchorHref(child);
            if (href != null) {
                return href;
            }
        }
        return null;
    }

    private void normalizeLinkElement(Element linkEl, String titleAnchorHref, String baseUrl) {
        if (linkEl == null || linkEl.getAttribute("href") != null) {
            return;
        }
        String childHref = findFirstAnchorHref(linkEl);
        String rawLink = linkEl.getValue().trim();
        boolean isMalformedLink = rawLink.isEmpty()
                || rawLink.contains("%3Ca")
                || rawLink.contains("%3ca")
                || rawLink.contains("<a ")
                || !linkEl.getChildren().isEmpty();

        if (!isMalformedLink) {
            return;
        }

        String candidateHref = titleAnchorHref != null ? titleAnchorHref : childHref;
        if (candidateHref == null && !rawLink.isEmpty()) {
            try {
                String decoded = URLDecoder.decode(rawLink, StandardCharsets.UTF_8);
                Matcher matcher = ANCHOR_HREF_PATTERN.matcher(decoded);
                if (matcher.find()) {
                    candidateHref = matcher.group(1).trim();
                }
            } catch (Exception ignored) {
                // Ignore decoding errors and leave original link intact
            }
        }

        if (candidateHref != null && !candidateHref.isBlank()) {
            linkEl.setText(resolveUrl(baseUrl, candidateHref));
        }
    }

    private String resolveUrl(String baseUrl, String href) {
        if (href == null || href.isBlank()) {
            return baseUrl;
        }
        try {
            if (baseUrl != null && !baseUrl.isBlank()) {
                return URI.create(baseUrl.trim()).resolve(href.trim()).toString();
            }
        } catch (Exception ignored) {
            // Fallback below
        }
        return href.trim();
    }

    private void normalizeAuthorElements(Element item) {
        for (Element child : item.getChildren()) {
            String name = child.getName();
            boolean isDcCreator = "creator".equalsIgnoreCase(name);
            boolean isRssAuthor = "author".equalsIgnoreCase(name)
                    && findChildByLocalName(child, "name") == null;
            if (isDcCreator || isRssAuthor) {
                if (!child.getChildren().isEmpty()) {
                    child.setText(child.getValue().trim());
                } else if (child.getText() != null) {
                    child.setText(child.getText().trim());
                }
            }
        }
    }

    private void normalizePubDateElement(Element pubDateEl) {
        if (pubDateEl == null) {
            return;
        }
        String raw = pubDateEl.getValue().trim();
        if (raw.length() >= 19 && Character.isDigit(raw.charAt(0)) && raw.contains("T")) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(raw, FLEXIBLE_ISO_OFFSET_DATE_TIME);
                pubDateEl.setText(odt.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            } catch (Exception ignored) {
                // Leave original pubDate text if it doesn't match ISO-8601
            }
        }
    }

    private void updateFeedMetadata(Feed feed, SyndFeed syndFeed) {
        if (syndFeed.getTitle() != null && !syndFeed.getTitle().isBlank()) {
            String currentTitle = feed.getTitle();
            if (currentTitle == null || currentTitle.isBlank() || currentTitle.equals(feed.getFeedUrl())) {
                feed.setTitle(truncate(syndFeed.getTitle().trim(), 255));
            }
        }
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
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img[^>]+src=[\"'](https?://[^\"'\\s>]+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern META_TAG_PATTERN = Pattern.compile(
            "<meta\\b[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern META_ATTR_PATTERN = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))"
    );

    /**
     * Processes a single feed entry. Returns true if a new article was saved,
     * false if it already existed (deduplicated by feed_id + guid).
     * Backfills missing metadata/thumbnails for existing articles if now available.
     */
    private boolean processEntry(Feed feed, SyndEntry entry, int[] ogBudget) {
        String guid = extractGuid(entry);
        String resolvedTitle = (entry.getTitle() != null && !entry.getTitle().isBlank())
                ? truncate(entry.getTitle().trim(), 512)
                : "Untitled";
        String resolvedAuthor = (entry.getAuthor() != null && !entry.getAuthor().isBlank())
                ? truncate(entry.getAuthor().trim(), 255)
                : null;
        String resolvedArticleUrl = (entry.getLink() != null && !entry.getLink().isBlank())
                ? entry.getLink().trim()
                : feed.getSiteUrl();

        var existingOpt = articleRepository.findByFeedIdAndGuid(feed.getId(), guid);
        if (existingOpt.isPresent()) {
            Article existing = existingOpt.get();
            boolean updated = false;
            if ((existing.getTitle() == null || existing.getTitle().isBlank() || "Untitled".equals(existing.getTitle()))
                    && !"Untitled".equals(resolvedTitle)) {
                existing.setTitle(resolvedTitle);
                existing.setFiltered(feed.matchesTitleFilter(resolvedTitle));
                if (entry.getPublishedDate() != null || entry.getUpdatedDate() != null) {
                    existing.setPublishedAt(extractPublishedDate(entry));
                }
                updated = true;
            }
            if ((existing.getAuthor() == null || existing.getAuthor().isBlank()) && resolvedAuthor != null) {
                existing.setAuthor(resolvedAuthor);
                updated = true;
            }
            if (resolvedArticleUrl != null
                    && (existing.getArticleUrl() == null
                        || existing.getArticleUrl().contains("%3Ca")
                        || existing.getArticleUrl().contains("%3ca"))) {
                existing.setArticleUrl(resolvedArticleUrl);
                updated = true;
            }
            if (existing.getImageUrl() == null) {
                String img = extractImageUrl(entry, existing);
                if (img == null && !existing.isFiltered()) {
                    img = tryFetchOpenGraphImage(existing.getArticleUrl(), ogBudget);
                }
                if (img != null) {
                    existing.setImageUrl(img);
                    updated = true;
                }
            }
            if (updated) {
                articleRepository.save(existing);
            }
            return false;
        }

        Article article = new Article();
        article.setFeed(feed);
        article.setGuid(guid);
        article.setTitle(resolvedTitle);
        article.setFiltered(feed.matchesTitleFilter(resolvedTitle));
        article.setAuthor(resolvedAuthor);
        article.setArticleUrl(resolvedArticleUrl);
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

        // Extract image using enclosures, Media RSS, inline <img>, or Open Graph (og:image / twitter:image) fallback
        String imageUrl = extractImageUrl(entry, article);
        if (imageUrl == null && !article.isFiltered()) {
            imageUrl = tryFetchOpenGraphImage(article.getArticleUrl(), ogBudget);
        }
        article.setImageUrl(imageUrl);

        articleRepository.save(article);
        return true;
    }

    /**
     * Backfills Open Graph thumbnails for recent non-filtered articles in this feed
     * that still lack an image (e.g., when a feed returns 304 Not Modified).
     */
    private void backfillMissingOpenGraphImages(Feed feed, int[] ogBudget) {
        if (openGraphRestClient == null || feed.getId() == null || ogBudget[0] <= 0) {
            return;
        }
        List<Article> missing = articleRepository
                .findTop20ByFeedIdAndImageUrlIsNullAndFilteredFalseOrderByPublishedAtDesc(feed.getId());
        for (Article article : missing) {
            if (ogBudget[0] <= 0) {
                break;
            }
            String img = tryFetchOpenGraphImage(article.getArticleUrl(), ogBudget);
            if (img != null) {
                article.setImageUrl(img);
                articleRepository.save(article);
            }
        }
    }

    /**
     * Fetches at most the first {@link #MAX_OG_HTML_BYTES} of {@code articleUrl} to extract
     * {@code og:image} or {@code twitter:image} metadata, enforcing SSRF validation across
     * redirects and probing each URL at most once per LRU cache lifetime.
     */
    private String tryFetchOpenGraphImage(String articleUrl, int[] ogBudget) {
        if (openGraphRestClient == null || ogBudget[0] <= 0 || articleUrl == null || articleUrl.isBlank()) {
            return null;
        }
        String trimmedUrl = articleUrl.trim();
        if (!ogProbedUrls.add(trimmedUrl)) {
            return null;
        }
        ogBudget[0]--;

        String currentUrl = trimmedUrl;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                UrlSafetyValidator.validate(currentUrl);

                FetchResult res = openGraphRestClient.get().uri(currentUrl).exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    HttpHeaders headers = response.getHeaders();
                    if (isRedirect(status.value()) || !status.is2xxSuccessful()) {
                        return new FetchResult(status.value(), headers, new byte[0]);
                    }
                    MediaType contentType = null;
                    try {
                        contentType = headers.getContentType();
                    } catch (Exception ignored) {
                        // Ignore malformed Content-Type header
                    }
                    if (contentType != null) {
                        String typeAndSubtype = (contentType.getType() + "/" + contentType.getSubtype())
                                .toLowerCase(Locale.ROOT);
                        if (typeAndSubtype.startsWith("image/")) {
                            return new FetchResult(status.value(), headers, new byte[0]);
                        }
                        if (!typeAndSubtype.contains("html") && !typeAndSubtype.contains("xml")) {
                            return new FetchResult(415, headers, new byte[0]);
                        }
                    }
                    byte[] prefix = response.getBody().readNBytes(MAX_OG_HTML_BYTES);
                    return new FetchResult(status.value(), headers, prefix);
                });

                if (isRedirect(res.statusCode())) {
                    if (hop == MAX_REDIRECTS) {
                        return null;
                    }
                    String location = res.headers().getFirst("Location");
                    if (location == null || location.isBlank()) {
                        return null;
                    }
                    currentUrl = URI.create(currentUrl).resolve(location.trim()).toString();
                    continue;
                }

                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    return null;
                }

                MediaType contentType = null;
                try {
                    contentType = res.headers().getContentType();
                } catch (Exception ignored) {
                    // Ignore
                }
                if (contentType != null && "image".equalsIgnoreCase(contentType.getType())) {
                    return currentUrl.length() <= 2048 ? currentUrl : null;
                }

                if (res.body() == null || res.body().length == 0) {
                    return null;
                }

                Charset charset = StandardCharsets.UTF_8;
                if (contentType != null && contentType.getCharset() != null) {
                    charset = contentType.getCharset();
                }
                String htmlSnippet = new String(res.body(), charset);
                return extractOpenGraphImageFromHtml(htmlSnippet, currentUrl);
            }
        } catch (Exception e) {
            log.debug("Could not extract Open Graph image from '{}': {}", articleUrl, e.getMessage());
        }
        return null;
    }

    static String extractOpenGraphImageFromHtml(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return null;
        }
        int headEnd = html.toLowerCase(Locale.ROOT).indexOf("</head>");
        String headSlice = headEnd >= 0 ? html.substring(0, headEnd) : html;

        String twitterFallback = null;
        Matcher tagMatcher = META_TAG_PATTERN.matcher(headSlice);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            String key = null;
            String content = null;

            Matcher attrMatcher = META_ATTR_PATTERN.matcher(tag);
            while (attrMatcher.find()) {
                String attrName = attrMatcher.group(1).toLowerCase(Locale.ROOT);
                String attrVal = attrMatcher.group(2) != null
                        ? attrMatcher.group(2)
                        : (attrMatcher.group(3) != null ? attrMatcher.group(3) : attrMatcher.group(4));
                if (attrVal == null) {
                    continue;
                }
                if ("property".equals(attrName) || "name".equals(attrName) || "itemprop".equals(attrName)) {
                    key = attrVal.trim().toLowerCase(Locale.ROOT);
                } else if ("content".equals(attrName)) {
                    content = attrVal;
                }
            }

            if (key == null || content == null || content.isBlank()) {
                continue;
            }

            if ("og:image".equals(key) || "og:image:url".equals(key) || "og:image:secure_url".equals(key)) {
                String resolved = resolveCandidateImageUrl(content, pageUrl);
                if (resolved != null) {
                    return resolved;
                }
            } else if (twitterFallback == null
                    && ("twitter:image".equals(key) || "twitter:image:src".equals(key))) {
                twitterFallback = resolveCandidateImageUrl(content, pageUrl);
            }
        }
        return twitterFallback;
    }

    private static String resolveCandidateImageUrl(String rawUrl, String pageUrl) {
        String unescaped = rawUrl.trim()
                .replace("&amp;", "&")
                .replace("&#38;", "&")
                .replace("&#x26;", "&")
                .replace("&#X26;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        if (unescaped.isEmpty() || unescaped.regionMatches(true, 0, "data:", 0, 5)) {
            return null;
        }
        String resolved = unescaped;
        try {
            if (pageUrl != null && !pageUrl.isBlank()) {
                resolved = URI.create(pageUrl.trim()).resolve(unescaped).toString();
            }
        } catch (Exception ignored) {
            // Keep unescaped candidate if URI resolution fails
        }
        String lower = resolved.toLowerCase(Locale.ROOT);
        if ((!lower.startsWith("http://") && !lower.startsWith("https://")) || resolved.length() > 2048) {
            return null;
        }
        if (lower.contains("feedburner.com") || lower.contains("1x1") || lower.contains("tracking") || lower.contains("beacon")) {
            return null;
        }
        return resolved;
    }

    /**
     * Extracts an image URL for the article using the following precedence:
     * 1. Standard RSS / Atom enclosures with an image MIME type.
     * 2. Media RSS (<media:content> or <media:thumbnail>) via ROME foreign markup.
     * 3. First <img> tag found in the HTML content or summary.
     */
    private String extractImageUrl(SyndEntry entry, Article article) {
        // 1. Check enclosures
        if (entry.getEnclosures() != null) {
            for (var enclosure : entry.getEnclosures()) {
                if (enclosure.getType() != null && enclosure.getType().startsWith("image/")) {
                    if (enclosure.getUrl() != null && !enclosure.getUrl().isBlank()) {
                        return enclosure.getUrl().trim();
                    }
                }
            }
        }

        // 2. Check Media RSS (<media:content> or <media:thumbnail>)
        if (entry.getForeignMarkup() != null) {
            for (var obj : entry.getForeignMarkup()) {
                if (obj instanceof Element el) {
                    String url = extractFromMediaElement(el);
                    if (url != null && !url.isBlank()) {
                        return url.trim();
                    }
                }
            }
        }

        // 3. Check <img> tag inside HTML content or summary
        String contentImg = extractFirstImgSrc(article.getContent());
        if (contentImg != null) {
            return contentImg;
        }

        return extractFirstImgSrc(article.getSummary());
    }

    private String extractFromMediaElement(Element el) {
        String name = el.getName().toLowerCase();
        String prefix = el.getNamespacePrefix() != null ? el.getNamespacePrefix().toLowerCase() : "";
        String nsUri = el.getNamespaceURI() != null ? el.getNamespaceURI().toLowerCase() : "";

        boolean isMediaNs = "media".equals(prefix) || nsUri.contains("mrss") || nsUri.contains("media");

        if (isMediaNs || "content".equals(name) || "thumbnail".equals(name)) {
            // Check <media:thumbnail url="..." />
            if ("thumbnail".equals(name)) {
                String url = el.getAttributeValue("url");
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }

            // Check <media:content url="..." />
            if ("content".equals(name)) {
                String medium = el.getAttributeValue("medium");
                String type = el.getAttributeValue("type");
                String url = el.getAttributeValue("url");

                boolean isImage = "image".equalsIgnoreCase(medium)
                        || (type != null && type.startsWith("image/"))
                        || (url != null && (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png") || url.contains(".webp") || url.contains(".gif")));

                if (url != null && !url.isBlank() && (isImage || (medium == null && type == null))) {
                    return url;
                }
            }
        }

        // Recursively inspect children (e.g. <media:group>)
        for (Element child : el.getChildren()) {
            String childUrl = extractFromMediaElement(child);
            if (childUrl != null) {
                return childUrl;
            }
        }

        return null;
    }

    private String extractFirstImgSrc(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher matcher = IMG_SRC_PATTERN.matcher(html);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            // Filter out tracking pixels and common analytics beacons
            String lower = url.toLowerCase();
            if (!lower.contains("feedburner.com") && !lower.contains("1x1") && !lower.contains("tracking") && !lower.contains("beacon")) {
                return url;
            }
        }
        return null;
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
