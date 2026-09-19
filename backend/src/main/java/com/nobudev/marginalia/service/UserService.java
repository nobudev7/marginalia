package com.nobudev.marginalia.service;

import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages user lookup, creation, and security context resolution.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the currently authenticated user from SecurityContextHolder.
     * Falls back to the first user in the database if called outside an authenticated
     * web request (e.g. direct controller test invocations).
     */
    @Transactional
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String email = extractEmail(auth);
            if (email != null && !email.isBlank()) {
                Optional<User> user = userRepository.findByEmail(email);
                if (user.isPresent()) {
                    return user.get();
                }
            }
        }

        // Fallback for tests / non-web execution
        return userRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> userRepository.save(
                        new User("dev@marginalia.local", "Dev User", null)));
    }

    /**
     * Finds an existing user by email or creates a new one.
     * Updates display name and avatar URL if they have changed.
     */
    @Transactional
    public User findOrCreateUser(String email, String displayName, String avatarUrl) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    boolean updated = false;
                    if (displayName != null && !displayName.isBlank() && !displayName.equals(existing.getDisplayName())) {
                        existing.setDisplayName(displayName);
                        updated = true;
                    }
                    if (avatarUrl != null && !avatarUrl.isBlank() && !avatarUrl.equals(existing.getAvatarUrl())) {
                        existing.setAvatarUrl(avatarUrl);
                        updated = true;
                    }
                    return updated ? userRepository.save(existing) : existing;
                })
                .orElseGet(() -> userRepository.save(new User(email, displayName, avatarUrl)));
    }

    public Optional<String> getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return Optional.ofNullable(extractEmail(auth));
        }
        return Optional.empty();
    }

    private String extractEmail(Authentication auth) {
        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return auth.getName();
    }
}
