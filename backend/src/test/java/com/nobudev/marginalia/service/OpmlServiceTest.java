package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class OpmlServiceTest {

    @Autowired
    private OpmlService opmlService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FeedRepository feedRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(new User("opml-test@marginalia.local", "OPML Tester", null));
    }

    @Test
    void testImportOpmlSuccessfully() {
        InputStream is = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        assertThat(is).isNotNull();

        List<FeedResponse> imported = opmlService.importOpml(is, testUser);

        // sample-subscriptions.opml has 2 in Technology, 1 in Design, 1 standalone = 4 feeds
        assertThat(imported).hasSize(4);

        List<Category> categories = categoryRepository.findByUserIdOrderBySortOrderAscNameAsc(testUser.getId());
        assertThat(categories).extracting(Category::getName)
                .containsExactlyInAnyOrder("Technology", "Design");

        List<Feed> feeds = feedRepository.findByUserIdOrderByTitleAsc(testUser.getId());
        assertThat(feeds).hasSize(4);
        assertThat(feeds).extracting(Feed::getTitle)
                .contains("TechCrunch", "Ars Technica", "Smashing Magazine", "Standalone Feed");
    }

    @Test
    void testImportOpmlSkipsDuplicates() {
        InputStream is1 = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        opmlService.importOpml(is1, testUser);

        // Second import of same file should import 0 new feeds
        InputStream is2 = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        List<FeedResponse> reimported = opmlService.importOpml(is2, testUser);

        assertThat(reimported).isEmpty();
        assertThat(feedRepository.findByUserIdOrderByTitleAsc(testUser.getId())).hasSize(4);
    }

    @Test
    void testExportOpml() {
        InputStream is = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        opmlService.importOpml(is, testUser);

        String opmlXml = opmlService.exportOpml(testUser);

        assertThat(opmlXml).isNotNull();
        assertThat(opmlXml).contains("<opml version=\"2.0\">");
        assertThat(opmlXml).contains("Marginalia Subscriptions");
        assertThat(opmlXml).contains("title=\"Technology\"");
        assertThat(opmlXml).contains("xmlUrl=\"https://techcrunch.com/feed/\"");
        assertThat(opmlXml).contains("xmlUrl=\"https://example.com/standalone.xml\"");
    }
}
