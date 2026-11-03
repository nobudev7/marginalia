package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.CategoryRequest;
import com.nobudev.marginalia.dto.CategoryResponse;
import com.nobudev.marginalia.entity.Category;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        int nextSortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : categoryRepository.findByUserIdOrderBySortOrderAscNameAsc(user.getId()).stream()
                        .mapToInt(Category::getSortOrder)
                        .max()
                        .orElse(-1) + 1;

        Category category = new Category(
                user,
                request.name(),
                nextSortOrder
        );
        category = categoryRepository.save(category);
        return CategoryResponse.from(category);
    }

    @Transactional
    public List<CategoryResponse> reorderCategories(List<Long> categoryIds, Long userId) {
        List<Category> userCategories = categoryRepository.findByUserIdOrderBySortOrderAscNameAsc(userId);
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : userCategories) {
            byId.put(c.getId(), c);
        }

        for (Long id : categoryIds) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("Category not found: " + id);
            }
        }

        int order = 0;
        Set<Long> seen = new HashSet<>();
        for (Long id : categoryIds) {
            if (seen.add(id)) {
                byId.get(id).setSortOrder(order++);
            }
        }
        for (Category c : userCategories) {
            if (!seen.contains(c.getId())) {
                c.setSortOrder(order++);
            }
        }

        categoryRepository.saveAll(userCategories);
        return getCategoriesByUser(userId);
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
