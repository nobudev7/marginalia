package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles OPML import and export for bulk feed subscription management.
 * Uses standard JDK XML APIs (javax.xml) — no extra dependencies needed.
 */
@Service
public class OpmlService {

    private static final Logger log = LoggerFactory.getLogger(OpmlService.class);

    private final FeedRepository feedRepository;
    private final CategoryRepository categoryRepository;

    public OpmlService(FeedRepository feedRepository,
                       CategoryRepository categoryRepository) {
        this.feedRepository = feedRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Import feeds from an OPML file. Nested outlines become categories;
     * leaf outlines with xmlUrl become feed subscriptions. Duplicate feeds
     * (same URL for same user) are skipped.
     *
     * @return list of newly imported feeds
     */
    public List<FeedResponse> importOpml(InputStream inputStream, User user) {
        List<FeedResponse> imported = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Security: disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            Element body = (Element) doc.getElementsByTagName("body").item(0);
            if (body == null) {
                throw new IllegalArgumentException("Invalid OPML: missing <body> element");
            }

            NodeList topOutlines = body.getChildNodes();
            for (int i = 0; i < topOutlines.getLength(); i++) {
                if (!(topOutlines.item(i) instanceof Element outline)) continue;
                if (!"outline".equalsIgnoreCase(outline.getTagName())) continue;

                String xmlUrl = outline.getAttribute("xmlUrl");
                if (xmlUrl != null && !xmlUrl.isBlank()) {
                    // Top-level feed (no category)
                    Feed feed = importFeedOutline(outline, user, null);
                    if (feed != null) {
                        imported.add(FeedResponse.from(feed));
                    }
                } else {
                    // Category folder — child outlines are feeds
                    String categoryName = outline.getAttribute("title");
                    if (categoryName == null || categoryName.isBlank()) {
                        categoryName = outline.getAttribute("text");
                    }
                    if (categoryName == null || categoryName.isBlank()) {
                        categoryName = "Imported";
                    }

                    Category category = getOrCreateCategory(user, categoryName);

                    NodeList children = outline.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        if (!(children.item(j) instanceof Element child)) continue;
                        if (!"outline".equalsIgnoreCase(child.getTagName())) continue;

                        Feed feed = importFeedOutline(child, user, category);
                        if (feed != null) {
                            imported.add(FeedResponse.from(feed));
                        }
                    }
                }
            }

            log.info("OPML import complete: {} feeds imported for user {}", imported.size(), user.getEmail());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OPML file: " + e.getMessage(), e);
        }

        return imported;
    }

    /**
     * Export all feeds for a user as an OPML 2.0 document.
     * Feeds are grouped by category; uncategorized feeds appear at the top level.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String exportOpml(User user) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Root <opml>
            Element opml = doc.createElement("opml");
            opml.setAttribute("version", "2.0");
            doc.appendChild(opml);

            // <head>
            Element head = doc.createElement("head");
            Element title = doc.createElement("title");
            title.setTextContent("Marginalia Subscriptions");
            head.appendChild(title);
            opml.appendChild(head);

            // <body>
            Element body = doc.createElement("body");
            opml.appendChild(body);

            List<Feed> feeds = feedRepository.findByUserIdOrderByTitleAsc(user.getId());

            // Group feeds by category
            List<Feed> uncategorized = new ArrayList<>();
            List<Category> seenCategories = new ArrayList<>();

            for (Feed feed : feeds) {
                if (feed.getCategory() == null) {
                    uncategorized.add(feed);
                } else if (!seenCategories.contains(feed.getCategory())) {
                    seenCategories.add(feed.getCategory());
                }
            }

            // Write categorized feeds
            for (Category category : seenCategories) {
                Element categoryOutline = doc.createElement("outline");
                categoryOutline.setAttribute("text", category.getName());
                categoryOutline.setAttribute("title", category.getName());

                for (Feed feed : feeds) {
                    if (feed.getCategory() != null && feed.getCategory().getId().equals(category.getId())) {
                        categoryOutline.appendChild(createFeedOutline(doc, feed));
                    }
                }

                body.appendChild(categoryOutline);
            }

            // Write uncategorized feeds
            for (Feed feed : uncategorized) {
                body.appendChild(createFeedOutline(doc, feed));
            }

            // Serialize to string
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OPML: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Feed importFeedOutline(Element outline, User user, Category category) {
        String xmlUrl = outline.getAttribute("xmlUrl");
        if (xmlUrl == null || xmlUrl.isBlank()) {
            return null;
        }

        // Skip if feed already exists for this user
        if (feedRepository.findByUserIdAndFeedUrl(user.getId(), xmlUrl).isPresent()) {
            log.debug("Skipping duplicate feed: {}", xmlUrl);
            return null;
        }

        String feedTitle = outline.getAttribute("title");
        if (feedTitle == null || feedTitle.isBlank()) {
            feedTitle = outline.getAttribute("text");
        }
        if (feedTitle == null || feedTitle.isBlank()) {
            feedTitle = xmlUrl;
        }

        String htmlUrl = outline.getAttribute("htmlUrl");

        Feed feed = new Feed(user, category, xmlUrl, htmlUrl, feedTitle, null);
        return feedRepository.save(feed);
    }

    private Category getOrCreateCategory(User user, String name) {
        return categoryRepository.findByUserIdAndName(user.getId(), name)
                .orElseGet(() -> {
                    Category category = new Category(user, name, 0);
                    return categoryRepository.save(category);
                });
    }

    private Element createFeedOutline(Document doc, Feed feed) {
        Element outline = doc.createElement("outline");
        outline.setAttribute("type", "rss");
        outline.setAttribute("text", feed.getTitle());
        outline.setAttribute("title", feed.getTitle());
        outline.setAttribute("xmlUrl", feed.getFeedUrl());
        if (feed.getSiteUrl() != null) {
            outline.setAttribute("htmlUrl", feed.getSiteUrl());
        }
        if (feed.getDescription() != null) {
            outline.setAttribute("description", feed.getDescription());
        }
        return outline;
    }
}
