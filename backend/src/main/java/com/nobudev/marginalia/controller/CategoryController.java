package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.CategoryRequest;
import com.nobudev.marginalia.dto.CategoryResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.CategoryService;
import com.nobudev.marginalia.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final UserService userService;

    public CategoryController(CategoryService categoryService, UserService userService) {
        this.categoryService = categoryService;
        this.userService = userService;
    }

    @GetMapping
    public List<CategoryResponse> getCategories() {
        User user = userService.getCurrentUser();
        return categoryService.getCategoriesByUser(user.getId());
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        User user = userService.getCurrentUser();
        CategoryResponse response = categoryService.createCategory(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
