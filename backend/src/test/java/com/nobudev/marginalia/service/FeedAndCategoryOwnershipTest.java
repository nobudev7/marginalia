package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CategoryRequest;
import com.nobudev.marginalia.dto.CategoryResponse;
import com.nobudev.marginalia.dto.FeedRequest;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class FeedAndCategoryOwnershipTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User userA;
    private User userB;
    private Category categoryB;
    private Feed feedB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(new User("userA@marginalia.local", "User A", null));
        userB = userRepository.save(new User("userB@marginalia.local", "User B", null));

        categoryB = categoryRepository.save(new Category(userB, "User B Category", 1));
        feedB = feedRepository.save(new Feed(userB, categoryB, "https://example.com/feed-b.xml", null, "Feed B", null));
    }

    @Test
    void userACannotGetFeedOfUserB() {
        assertThatThrownBy(() -> feedService.getFeed(feedB.getId(), userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Feed not found");
    }

    @Test
    void userACannotUpdateFeedOfUserB() {
        FeedRequest updateRequest = new FeedRequest("https://example.com/feed-b.xml", "Hacked Title", null);
        assertThatThrownBy(() -> feedService.updateFeed(feedB.getId(), updateRequest, userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Feed not found");
    }

    @Test
    void userACannotDeleteFeedOfUserB() {
        assertThatThrownBy(() -> feedService.deleteFeed(feedB.getId(), userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Feed not found");
    }

    @Test
    void userACannotAttachFeedToCategoryOfUserB() {
        FeedRequest addRequest = new FeedRequest("https://example.com/new-feed.xml", "New Feed", categoryB.getId());
        assertThatThrownBy(() -> feedService.addFeed(addRequest, userA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void userACannotUpdateCategoryOfUserB() {
        CategoryRequest updateRequest = new CategoryRequest("Hacked Category", 99);
        assertThatThrownBy(() -> categoryService.updateCategory(categoryB.getId(), updateRequest, userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void userACannotDeleteCategoryOfUserB() {
        assertThatThrownBy(() -> categoryService.deleteCategory(categoryB.getId(), userA.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void userBCanAccessOwnFeedAndCategory() {
        FeedResponse feed = feedService.getFeed(feedB.getId(), userB.getId());
        assertThat(feed).isNotNull();
        assertThat(feed.title()).isEqualTo("Feed B");

        CategoryResponse updatedCat = categoryService.updateCategory(categoryB.getId(), new CategoryRequest("Renamed", 2), userB.getId());
        assertThat(updatedCat.name()).isEqualTo("Renamed");
    }
}
