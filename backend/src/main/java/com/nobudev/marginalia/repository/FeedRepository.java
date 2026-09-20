package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.Feed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedRepository extends JpaRepository<Feed, Long> {
    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<Feed> findById(Long id);

    @EntityGraph(attributePaths = {"category"})
    Optional<Feed> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category"})
    List<Feed> findByUserIdOrderByTitleAsc(Long userId);

    List<Feed> findByUserId(Long userId);

    List<Feed> findByUserIdAndCategoryIdOrderByTitleAsc(Long userId, Long categoryId);
    List<Feed> findByUserIdAndCategoryIsNullOrderByTitleAsc(Long userId);

    @EntityGraph(attributePaths = {"category"})
    Optional<Feed> findByUserIdAndFeedUrl(Long userId, String feedUrl);
}
