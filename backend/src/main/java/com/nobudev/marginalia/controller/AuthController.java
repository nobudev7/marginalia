package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.AuthResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.AuthWhitelistService;
import com.nobudev.marginalia.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthWhitelistService whitelistService;

    @Value("${app.auth.dev-mode:false}")
    private boolean devMode;

    public AuthController(UserService userService, AuthWhitelistService whitelistService) {
        this.userService = userService;
        this.whitelistService = whitelistService;
    }

    /**
     * Returns the currently authenticated user profile.
     * Returns 401 Unauthorized if the user is not authenticated.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = userService.getCurrentUser();
            return ResponseEntity.ok(AuthResponse.from(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Non-throwing authentication status check for frontend bootstrap.
     */
    @GetMapping("/status")
    public Map<String, Object> getAuthStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            try {
                User user = userService.getCurrentUser();
                return Map.of(
                        "authenticated", true,
                        "user", AuthResponse.from(user)
                );
            } catch (Exception ignored) {
            }
        }
        return Map.of(
                "authenticated", false
        );
    }

    /**
     * Returns standard OAuth2 initiation URLs.
     */
    @GetMapping("/providers")
    public Map<String, String> getProviders() {
        return Map.of(
                "google", "/oauth2/authorization/google",
                "github", "/oauth2/authorization/github"
        );
    }

    /**
     * Local development login helper. Sets up an authenticated session cookie
     * without requiring real Google/GitHub OAuth2 credentials.
     * Active only when app.auth.dev-mode=true (disabled in production).
     */
    @RequestMapping(value = "/dev-login", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> devLogin(
            @RequestParam(defaultValue = "test@example.com") String email,
            HttpServletRequest request) {
        if (!devMode) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (!whitelistService.isWhitelisted(normalizedEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Access Denied",
                    "message", "Email '" + normalizedEmail + "' is not on the authorized whitelist."
            ));
        }

        User user = userService.findOrCreateUser(normalizedEmail, "Dev Tester", null);

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(normalizedEmail, null, List.of(authority));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return ResponseEntity.ok(Map.of(
                "message", "Authenticated successfully for local development",
                "email", normalizedEmail,
                "userId", user.getId(),
                "sessionId", session.getId()
        ));
    }
}
