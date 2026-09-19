package com.nobudev.marginalia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WhitelistRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,
        String note
) {
}
