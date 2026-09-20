package com.nobudev.marginalia.service;

import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetCurrentUserReturnsAuthenticatedUser() {
        User user = userRepository.save(new User("user-service-test@marginalia.local", "Service Tester", null));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);

        User current = userService.getCurrentUser();
        assertThat(current).isNotNull();
        assertThat(current.getEmail()).isEqualTo("user-service-test@marginalia.local");
    }

    @Test
    void testGetCurrentUserThrowsAccessDeniedWhenNoAuthenticationInContext() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> userService.getCurrentUser())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No authenticated user found in security context");
    }

    @Test
    void testGetCurrentUserThrowsAccessDeniedForAnonymousUser() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        SecurityContextHolder.setContext(context);

        assertThatThrownBy(() -> userService.getCurrentUser())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No authenticated user found in security context");
    }
}
