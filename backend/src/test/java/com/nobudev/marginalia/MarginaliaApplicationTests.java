package com.nobudev.marginalia;

import com.nobudev.marginalia.entity.*;
import com.nobudev.marginalia.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MarginaliaApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleUserStateRepository userStateRepository;

    @Test
    void contextLoads() {
        assertThat(userRepository).isNotNull();
    }

    @Test
    void testInitialSchemaAndEntityPersistence() {
        // 1. Create User
        User user = new User("user1@example.com", "User1", "https://example.com/avatar.png");
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        // 2. Create Category
        Category category = new Category(user, "Engineering", 1);
        category = categoryRepository.save(category);
        assertThat(category.getId()).isNotNull();

        // 3. Create Feed
        Feed feed = new Feed(
                user,
                category,
                "https://example.com/feed.xml",
                "https://example.com",
                "Tech Blog",
                "A blog about tech and systems"
        );
        feed = feedRepository.save(feed);
        assertThat(feed.getId()).isNotNull();

        // 4. Create Article
        Article article = new Article(
                feed,
                "urn:uuid:article-1",
                "Introduction to Marginalia",
                "Author One",
                "<p>Full content here</p>",
                "Summary snippet",
                "https://example.com/posts/1",
                "https://example.com/posts/1.jpg",
                LocalDateTime.now()
        );
        article = articleRepository.save(article);
        assertThat(article.getId()).isNotNull();

        // 5. Create Article User State
        ArticleUserState state = new ArticleUserState(user, article);
        state.setRead(true);
        state.setSaved(true);
        state = userStateRepository.save(state);
        assertThat(state.getId()).isNotNull();
        assertThat(state.isRead()).isTrue();
        assertThat(state.isSaved()).isTrue();
        assertThat(state.getReadAt()).isNotNull();
        assertThat(state.getSavedAt()).isNotNull();

        // 6. Verify Repository Queries
        List<Category> categories = categoryRepository.findByUserIdOrderBySortOrderAscNameAsc(user.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Engineering");

        List<Feed> feeds = feedRepository.findByUserIdAndCategoryIdOrderByTitleAsc(user.getId(), category.getId());
        assertThat(feeds).hasSize(1);
        assertThat(feeds.get(0).getTitle()).isEqualTo("Tech Blog");

        var articles = articleRepository.findByFeedIdAndFeedUserIdOrderByPublishedAtDesc(feed.getId(), user.getId(), PageRequest.of(0, 10));
        assertThat(articles.getContent()).hasSize(1);
        assertThat(articles.getContent().get(0).getTitle()).isEqualTo("Introduction to Marginalia");

        var savedState = userStateRepository.findByUserIdAndArticleId(user.getId(), article.getId());
        assertThat(savedState).isPresent();
        assertThat(savedState.get().isSaved()).isTrue();
    }
}
