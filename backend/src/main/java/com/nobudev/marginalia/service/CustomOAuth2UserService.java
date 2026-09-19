package com.nobudev.marginalia.service;

import com.nobudev.marginalia.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Custom OAuth2 user service supporting providers like GitHub.
 * Resolves email (with fallback to GitHub /user/emails API for private emails),
 * validates against the email whitelist, and persists/updates the User entity.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final AuthWhitelistService whitelistService;
    private final UserService userService;
    private final RestClient restClient;

    public CustomOAuth2UserService(AuthWhitelistService whitelistService,
                                   UserService userService,
                                   RestClient restClient) {
        this.whitelistService = whitelistService;
        this.userService = userService;
        this.restClient = restClient;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String avatarUrl = oauth2User.getAttribute("avatar_url"); // GitHub avatar

        if (avatarUrl == null) {
            avatarUrl = oauth2User.getAttribute("picture"); // Google/standard avatar
        }

        // GitHub private email fallback: query the authenticated /user/emails endpoint
        if ((email == null || email.isBlank()) && "github".equalsIgnoreCase(registrationId)) {
            email = fetchGitHubPrimaryEmail(userRequest.getAccessToken().getTokenValue());
        }

        if (name == null || name.isBlank()) {
            name = oauth2User.getAttribute("login"); // GitHub handle
        }

        if (email == null || email.isBlank()) {
            log.warn("OAuth2 login failed: No email returned by provider '{}'", registrationId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email", "Email address not provided by " + registrationId, null)
            );
        }

        // Enforce whitelist check
        if (!whitelistService.isWhitelisted(email)) {
            log.warn("OAuth2 login rejected: Email '{}' is not on the whitelist", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_denied", "Unauthorized email address: " + email, null)
            );
        }

        // Save or update user in database
        User user = userService.findOrCreateUser(email, name, avatarUrl);
        log.info("OAuth2 login successful for user '{}' ({}) via {}", user.getEmail(), user.getId(), registrationId);

        // Build augmented attributes ensuring "email" is always present
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("email", email);
        if (name != null) {
            attributes.put("name", name);
        }

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (userNameAttributeName == null || userNameAttributeName.isBlank()) {
            userNameAttributeName = "email";
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oauth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        return new DefaultOAuth2User(authorities, attributes, userNameAttributeName);
    }

    private String fetchGitHubPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (emails != null) {
                return emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                        .map(e -> (String) e.get("email"))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch private emails from GitHub API: {}", e.getMessage());
        }
        return null;
    }
}
