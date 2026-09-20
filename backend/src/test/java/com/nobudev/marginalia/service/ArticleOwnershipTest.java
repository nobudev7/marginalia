package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.ArticleResponse;
import com.nobudev.marginalia.entity.Article;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.Feed;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.ArticleRepository;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ArticleOwnershipTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ArticleRepository articleRepository;

    private User userA;
    private User userB;
    private Category categoryB;
    private Feed feedB;
    private Article articleB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User("userA@marginalia.local", "User A", null));
        userB = userRepository.save(new User("userB@marginalia.local", "User B", null));

        categoryB = categoryRepository.save(new Category(userB, "User B Category", 1));
        feedB = feedRepository.save(new Feed(userB, categoryB, "https://example.com/feed-b.xml", null, "Feed B", null));
        articleB = articleRepository.save(new Article(
                feedB,
                "guid-b-1",
                "Article B",
                "Author B",
                "Content B",
                "Summary B",
                "https://example.com/b/1",
                null,
                LocalDateTime.now()
        ));
    }

    @Test
    void userACannotGetArticleOfUserB() {
        assertThatThrownBy(() -> articleService.getArticle(articleB.getId(), userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article not found");
    }

    @Test
    void userACannotMarkReadArticleOfUserB() {
        assertThatThrownBy(() -> articleService.markAsRead(userA.getId(), articleB.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article not found");
    }

    @Test
    void userACannotToggleSavedArticleOfUserB() {
        assertThatThrownBy(() -> articleService.toggleSaved(userA.getId(), articleB.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Article not found");
    }

    @Test
    void userAQueryingFeedOfUserBReturnsEmptyPage() {
        Page<ArticleResponse> page = articleService.getArticlesByFeed(feedB.getId(), userA.getId(), PageRequest.of(0, 20));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void userAQueryingCategoryOfUserBReturnsEmptyPage() {
        Page<ArticleResponse> page = articleService.getArticlesByCategory(categoryB.getId(), userA.getId(), PageRequest.of(0, 20));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void userBCanAccessOwnArticle() {
        ArticleResponse response = articleService.getArticle(articleB.getId(), userB.getId());
        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Article B");

        articleService.markAsRead(userB.getId(), articleB.getId(), true);
        ArticleResponse updated = articleService.getArticle(articleB.getId(), userB.getId());
        assertThat(updated.isRead()).isTrue();
    }
}
