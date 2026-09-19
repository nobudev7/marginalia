package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.WhitelistResponse;
import com.nobudev.marginalia.entity.WhitelistEntry;
import com.nobudev.marginalia.repository.WhitelistRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the email whitelist for authentication and access control.
 * Whitelist is primarily maintained in the database table 'whitelist_emails',
 * backed by a bootstrap environment variable ('app.auth.whitelist-emails')
 * to ensure instance administrators are never locked out.
 */
@Service
public class AuthWhitelistService {

    private static final Logger log = LoggerFactory.getLogger(AuthWhitelistService.class);

    private final WhitelistRepository whitelistRepository;
    private final Set<String> bootstrapAdminEmails;

    public AuthWhitelistService(WhitelistRepository whitelistRepository,
                                @Value("${app.auth.whitelist-emails:}") String whitelistConfig) {
        this.whitelistRepository = whitelistRepository;
        if (whitelistConfig == null || whitelistConfig.isBlank()) {
            this.bootstrapAdminEmails = Set.of();
        } else {
            this.bootstrapAdminEmails = Arrays.stream(whitelistConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * Seeds any bootstrap administrator emails into the database table on startup.
     */
    @PostConstruct
    @Transactional
    public void initBootstrapEmails() {
        for (String adminEmail : bootstrapAdminEmails) {
            if (!whitelistRepository.existsByEmailIgnoreCase(adminEmail)) {
                log.info("Seeding bootstrap administrator email into database whitelist: {}", adminEmail);
                whitelistRepository.save(new WhitelistEntry(adminEmail, "Bootstrap Administrator", "SYSTEM"));
            }
        }
    }

    /**
     * Checks if the given email is permitted to log in.
     * Evaluates both bootstrap administrator configuration and database table entries.
     */
    @Transactional(readOnly = true)
    public boolean isWhitelisted(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();

        // Safety measure 1: Bootstrap emails always have access even if DB table is emptied
        if (bootstrapAdminEmails.contains(normalized)) {
            return true;
        }

        // Safety measure 2: Check database whitelist table
        return whitelistRepository.existsByEmailIgnoreCase(normalized);
    }

    /**
     * Checks whether an email has administrator privileges to manage the whitelist.
     */
    public boolean isAdmin(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        // If bootstrap admins are configured, only they have admin access.
        // If not configured, any whitelisted user has administrative access.
        if (!bootstrapAdminEmails.isEmpty()) {
            return bootstrapAdminEmails.contains(normalized);
        }
        return isWhitelisted(normalized);
    }

    /**
     * Lists all whitelist entries, ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<WhitelistResponse> getAll() {
        return whitelistRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(WhitelistResponse::from)
                .toList();
    }

    /**
     * Adds an email to the whitelist.
     */
    @Transactional
    public WhitelistResponse addEmail(String email, String note, String addedBy) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        String normalized = email.trim().toLowerCase();

        if (whitelistRepository.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Email is already whitelisted: " + email);
        }

        WhitelistEntry entry = new WhitelistEntry(normalized, note, addedBy);
        WhitelistEntry saved = whitelistRepository.save(entry);
        log.info("Email '{}' added to whitelist by '{}'", normalized, addedBy);
        return WhitelistResponse.from(saved);
    }

    /**
     * Removes an email from the whitelist by entry ID.
     * Enforces anti-self-deletion and bootstrap protection safety measures.
     */
    @Transactional
    public void removeEmail(Long id, String currentAdminEmail) {
        WhitelistEntry entry = whitelistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Whitelist entry not found: " + id));

        validateRemovalSafety(entry.getEmail(), currentAdminEmail);

        whitelistRepository.delete(entry);
        log.info("Email '{}' (id: {}) removed from whitelist by '{}'", entry.getEmail(), id, currentAdminEmail);
    }

    /**
     * Removes an email from the whitelist by email address.
     */
    @Transactional
    public void removeEmailByAddress(String email, String currentAdminEmail) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        String normalized = email.trim().toLowerCase();
        WhitelistEntry entry = whitelistRepository.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Email not found in whitelist: " + email));

        validateRemovalSafety(entry.getEmail(), currentAdminEmail);

        whitelistRepository.delete(entry);
        log.info("Email '{}' removed from whitelist by '{}'", normalized, currentAdminEmail);
    }

    private void validateRemovalSafety(String targetEmail, String currentAdminEmail) {
        String normalizedTarget = targetEmail.trim().toLowerCase();
        String normalizedAdmin = currentAdminEmail != null ? currentAdminEmail.trim().toLowerCase() : "";

        // Safety measure 3: Anti-self-deletion
        if (normalizedTarget.equalsIgnoreCase(normalizedAdmin)) {
            throw new IllegalArgumentException("Cannot remove your own email from the whitelist");
        }

        // Safety measure 4: Bootstrap admin protection
        if (bootstrapAdminEmails.contains(normalizedTarget)) {
            throw new IllegalArgumentException(
                    "Cannot remove bootstrap administrator email '" + targetEmail +
                    "' via API. Remove it from AUTH_WHITELIST_EMAILS in environment configuration instead."
            );
        }
    }

    public Set<String> getBootstrapAdminEmails() {
        return bootstrapAdminEmails;
    }
}
