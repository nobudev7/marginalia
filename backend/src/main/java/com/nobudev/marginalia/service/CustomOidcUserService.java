package com.nobudev.marginalia.service;

import com.nobudev.marginalia.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Custom OpenID Connect (OIDC) user service for Google sign-in.
 * Validates the email against the whitelist and persists/updates the User entity.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);

    private final AuthWhitelistService whitelistService;
    private final UserService userService;

    public CustomOidcUserService(AuthWhitelistService whitelistService, UserService userService) {
        this.whitelistService = whitelistService;
        this.userService = userService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            email = oidcUser.getAttribute("email");
        }

        if (email == null || email.isBlank()) {
            log.warn("OIDC login failed: No email returned by provider '{}'", registrationId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email", "Email address not provided by " + registrationId, null)
            );
        }

        // Reject unverified email claims to prevent identity spoofing
        Boolean emailVerified = oidcUser.getAttribute("email_verified");
        if (Boolean.FALSE.equals(emailVerified)) {
            log.warn("OIDC login rejected: Email '{}' is not verified by provider '{}'", email, registrationId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email",
                            "Email address not verified by " + registrationId, null)
            );
        }

        // Enforce whitelist check
        if (!whitelistService.isWhitelisted(email)) {
            log.warn("OIDC login rejected: Email '{}' is not on the whitelist", email);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("access_denied", "Unauthorized email address: " + email, null)
            );
        }

        String name = oidcUser.getFullName();
        if (name == null || name.isBlank()) {
            name = oidcUser.getAttribute("name");
        }

        String picture = oidcUser.getPicture();
        if (picture == null || picture.isBlank()) {
            picture = oidcUser.getAttribute("picture");
        }

        User user = userService.findOrCreateUser(email, name, picture);
        log.info("OIDC login successful for user '{}' ({}) via {}", user.getEmail(), user.getId(), registrationId);

        return oidcUser;
    }
}
