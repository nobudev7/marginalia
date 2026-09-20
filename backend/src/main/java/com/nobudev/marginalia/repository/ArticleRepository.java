package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * Counts unread articles for a user. An article is "unread" if no
     * ArticleUserState row exists with is_read = true. This correctly handles
     * the sparse state table where newly crawled articles have no state row.
     */
    @Query("""
        SELECT COUNT(a) FROM Article a
        WHERE a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    long countUnreadByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT a.feed.id, COUNT(a) FROM Article a
        WHERE a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
        GROUP BY a.feed.id
    """)
    java.util.List<Object[]> countUnreadGroupedByFeed(@Param("userId") Long userId);

    @Override
    @EntityGraph(attributePaths = {"feed"})
    Optional<Article> findById(Long id);

    @EntityGraph(attributePaths = {"feed"})
    Optional<Article> findByIdAndFeedUserId(Long id, Long userId);

    Optional<Article> findByFeedIdAndGuid(Long feedId, String guid);

    @EntityGraph(attributePaths = {"feed"})
    Page<Article> findByFeedIdAndFeedUserIdOrderByPublishedAtDesc(Long feedId, Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"feed"})
    Page<Article> findByFeedCategoryIdAndFeedUserIdOrderByPublishedAtDesc(Long categoryId, Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"feed"})
    Page<Article> findByFeedUserIdOrderByPublishedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"feed"})
    @Query(value = """
        SELECT a FROM Article a
        WHERE a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
        ORDER BY a.publishedAt DESC
    """,
    countQuery = """
        SELECT COUNT(a) FROM Article a
        WHERE a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    Page<Article> findUnreadByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"feed"})
    @Query(value = """
        SELECT a FROM Article a
        WHERE a.feed.id = :feedId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
        ORDER BY a.publishedAt DESC
    """,
    countQuery = """
        SELECT COUNT(a) FROM Article a
        WHERE a.feed.id = :feedId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    Page<Article> findUnreadByFeedIdAndUserId(@Param("feedId") Long feedId, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"feed"})
    @Query(value = """
        SELECT a FROM Article a
        WHERE a.feed.category.id = :categoryId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
        ORDER BY a.publishedAt DESC
    """,
    countQuery = """
        SELECT COUNT(a) FROM Article a
        WHERE a.feed.category.id = :categoryId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    Page<Article> findUnreadByCategoryIdAndUserId(@Param("categoryId") Long categoryId, @Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT a.id FROM Article a
        WHERE a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    java.util.List<Long> findUnreadArticleIdsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT a.id FROM Article a
        WHERE a.feed.id = :feedId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    java.util.List<Long> findUnreadArticleIdsByFeedIdAndUserId(@Param("feedId") Long feedId, @Param("userId") Long userId);

    @Query("""
        SELECT a.id FROM Article a
        WHERE a.feed.category.id = :categoryId
          AND a.feed.user.id = :userId
          AND NOT EXISTS (
              SELECT 1 FROM ArticleUserState s
              WHERE s.article.id = a.id
                AND s.user.id = :userId
                AND s.isRead = true
          )
    """)
    java.util.List<Long> findUnreadArticleIdsByCategoryIdAndUserId(@Param("categoryId") Long categoryId, @Param("userId") Long userId);

    boolean existsByFeedIdAndGuid(Long feedId, String guid);
}
