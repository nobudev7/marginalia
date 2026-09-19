package com.nobudev.marginalia.service;

import com.nobudev.marginalia.dto.WhitelistResponse;
import com.nobudev.marginalia.entity.WhitelistEntry;
import com.nobudev.marginalia.repository.WhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthWhitelistServiceTest {

    @Mock
    private WhitelistRepository whitelistRepository;

    private AuthWhitelistService service;

    @BeforeEach
    void setUp() {
        service = new AuthWhitelistService(whitelistRepository, "admin@marginalia.local, Owner@example.com ");
    }

    @Test
    void testBootstrapEmailsMatchCaseInsensitive() {
        assertThat(service.isWhitelisted("owner@example.com")).isTrue();
        assertThat(service.isWhitelisted("OWNER@EXAMPLE.COM")).isTrue();
        assertThat(service.isWhitelisted("admin@marginalia.local")).isTrue();
        // Repository should not even be queried for bootstrap admins
        verify(whitelistRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void testDatabaseWhitelistLookup() {
        when(whitelistRepository.existsByEmailIgnoreCase("friend@example.com")).thenReturn(true);
        when(whitelistRepository.existsByEmailIgnoreCase("stranger@example.com")).thenReturn(false);

        assertThat(service.isWhitelisted("friend@example.com")).isTrue();
        assertThat(service.isWhitelisted("stranger@example.com")).isFalse();
    }

    @Test
    void testNullAndEmptyEmailIsRejected() {
        assertThat(service.isWhitelisted(null)).isFalse();
        assertThat(service.isWhitelisted("")).isFalse();
        assertThat(service.isWhitelisted("   ")).isFalse();
    }

    @Test
    void testAddEmailSuccess() {
        when(whitelistRepository.existsByEmailIgnoreCase("colleague@example.com")).thenReturn(false);
        when(whitelistRepository.save(any(WhitelistEntry.class))).thenAnswer(invocation -> {
            WhitelistEntry entry = invocation.getArgument(0);
            entry.setId(1L);
            return entry;
        });

        WhitelistResponse response = service.addEmail("Colleague@example.com ", "Work teammate", "admin@marginalia.local");

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("colleague@example.com");
        assertThat(response.note()).isEqualTo("Work teammate");
        assertThat(response.addedBy()).isEqualTo("admin@marginalia.local");
    }

    @Test
    void testAddDuplicateEmailThrowsException() {
        when(whitelistRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.addEmail("existing@example.com", "Note", "admin@marginalia.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already whitelisted");
    }

    @Test
    void testRemoveSelfIsBlockedBySafetyMeasure() {
        WhitelistEntry entry = new WhitelistEntry("admin@marginalia.local", "Admin", "SYSTEM");
        entry.setId(10L);
        when(whitelistRepository.findById(10L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.removeEmail(10L, "admin@marginalia.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove your own email");
    }

    @Test
    void testRemoveBootstrapAdminIsBlockedBySafetyMeasure() {
        WhitelistEntry entry = new WhitelistEntry("owner@example.com", "Owner", "SYSTEM");
        entry.setId(20L);
        when(whitelistRepository.findById(20L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.removeEmail(20L, "otheradmin@marginalia.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove bootstrap administrator email");
    }

    @Test
    void testRemoveRegularEmailSucceeds() {
        WhitelistEntry entry = new WhitelistEntry("friend@example.com", "Guest", "admin@marginalia.local");
        entry.setId(30L);
        when(whitelistRepository.findById(30L)).thenReturn(Optional.of(entry));

        service.removeEmail(30L, "admin@marginalia.local");

        verify(whitelistRepository, times(1)).delete(entry);
    }
}
