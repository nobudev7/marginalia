package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    Optional<Article> findByFeedIdAndGuid(Long feedId, String guid);
    Page<Article> findByFeedIdOrderByPublishedAtDesc(Long feedId, Pageable pageable);
    Page<Article> findByFeedCategoryIdOrderByPublishedAtDesc(Long categoryId, Pageable pageable);
    boolean existsByFeedIdAndGuid(Long feedId, String guid);
}
