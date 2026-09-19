package com.nobudev.marginalia.controller;

import com.nobudev.marginalia.dto.WhitelistRequest;
import com.nobudev.marginalia.dto.WhitelistResponse;
import com.nobudev.marginalia.entity.User;
import com.nobudev.marginalia.service.AuthWhitelistService;
import com.nobudev.marginalia.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/whitelist")
public class AdminWhitelistController {

    private final AuthWhitelistService whitelistService;
    private final UserService userService;

    public AdminWhitelistController(AuthWhitelistService whitelistService, UserService userService) {
        this.whitelistService = whitelistService;
        this.userService = userService;
    }

    private void verifyAdminAccess(User user) {
        if (!whitelistService.isAdmin(user.getEmail())) {
            throw new AccessDeniedException("Administrative privileges required to manage whitelist");
        }
    }

    @GetMapping
    public List<WhitelistResponse> getWhitelist() {
        User user = userService.getCurrentUser();
        verifyAdminAccess(user);
        return whitelistService.getAll();
    }

    @PostMapping
    public ResponseEntity<WhitelistResponse> addEmail(@Valid @RequestBody WhitelistRequest request) {
        User user = userService.getCurrentUser();
        verifyAdminAccess(user);
        WhitelistResponse response = whitelistService.addEmail(request.email(), request.note(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeEmail(@PathVariable Long id) {
        User user = userService.getCurrentUser();
        verifyAdminAccess(user);
        whitelistService.removeEmail(id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeEmailByAddress(@RequestParam String email) {
        User user = userService.getCurrentUser();
        verifyAdminAccess(user);
        whitelistService.removeEmailByAddress(email, user.getEmail());
        return ResponseEntity.noContent().build();
    }
}
