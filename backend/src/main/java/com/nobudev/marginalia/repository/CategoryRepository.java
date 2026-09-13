package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrderBySortOrderAscNameAsc(Long userId);
    Optional<Category> findByUserIdAndName(Long userId, String name);
}
