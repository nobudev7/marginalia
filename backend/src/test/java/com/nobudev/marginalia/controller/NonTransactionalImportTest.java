package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.CrawlResult;
import com.nobudev.marginalia.dto.FeedResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import com.nobudev.marginalia.repository.FeedRepository;
import com.nobudev.marginalia.repository.UserRepository;
import com.nobudev.marginalia.service.FeedCrawlerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests OPML import without @Transactional on the test class.
 * This ensures there is no ambient Hibernate session, verifying that
 * lazy proxies do not throw LazyInitializationException during DTO mapping.
 */
@SpringBootTest
class NonTransactionalImportTest {

    @Autowired
    private OpmlController opmlController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("nontx-test@marginalia.local", "Non-Tx Tester", null));
        org.springframework.security.core.context.SecurityContext context =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getEmail(), null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        feedRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testOpmlImportWithoutAmbientSession() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/sample-subscriptions.opml");
        assertThat(is).isNotNull();
        MockMultipartFile file = new MockMultipartFile("file", "subscriptions.opml", "text/xml", is.readAllBytes());

        List<FeedResponse> imported = opmlController.importOpml(file);
        assertThat(imported).hasSize(4);

        // Verify that category names were properly resolved without LazyInitializationException
        List<String> categoryNames = imported.stream()
                .map(FeedResponse::categoryName)
                .filter(name -> name != null)
                .toList();
        assertThat(categoryNames).contains("Technology", "Design");
    }
}
