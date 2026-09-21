package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.ArticleUserState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleUserStateRepository extends JpaRepository<ArticleUserState, Long> {
    Optional<ArticleUserState> findByUserIdAndArticleId(Long userId, Long articleId);
    List<ArticleUserState> findByUserIdAndArticleIdIn(Long userId, List<Long> articleIds);

    @EntityGraph(attributePaths = {"article", "article.feed"})
    @Query("SELECT s FROM ArticleUserState s WHERE s.user.id = :userId AND s.isSaved = true AND s.article.feed.user.id = :userId")
    Page<ArticleUserState> findByUserIdAndIsSavedTrue(@Param("userId") Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);
}
