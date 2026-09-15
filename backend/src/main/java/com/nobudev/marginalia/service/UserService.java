package com.nobudev.marginalia.service;

import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages user lookup and creation.
 * TODO: Phase 3 will update getCurrentUser() to resolve the OAuth2-authenticated principal.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the current user.
     * In Phase 3, this will be resolved from the OAuth2 security context.
     * For development, returns the first user or creates a dev user.
     */
    @Transactional
    public User getCurrentUser() {
        return userRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> userRepository.save(
                        new User("dev@marginalia.local", "Dev User", null)));
    }
}
