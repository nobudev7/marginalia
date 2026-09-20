package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CategoryRequest;
import com.nobudev.marginalia.dto.CategoryResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategoriesByUser(Long userId) {
        return categoryRepository.findByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, User user) {
        categoryRepository.findByUserIdAndName(user.getId(), request.name())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Category already exists: " + request.name());
                });

        Category category = new Category(
                user,
                request.name(),
                request.sortOrder() != null ? request.sortOrder() : 0
        );
        category = categoryRepository.save(category);
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found: " + categoryId));

        if (request.name() != null) {
            category.setName(request.name());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }

        category = categoryRepository.save(category);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        categoryRepository.delete(category);
    }
}
