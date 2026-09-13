package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.Feed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedRepository extends JpaRepository<Feed, Long> {
    List<Feed> findByUserIdOrderByTitleAsc(Long userId);
    List<Feed> findByUserIdAndCategoryIdOrderByTitleAsc(Long userId, Long categoryId);
    List<Feed> findByUserIdAndCategoryIsNullOrderByTitleAsc(Long userId);
    Optional<Feed> findByUserIdAndFeedUrl(Long userId, String feedUrl);
}
