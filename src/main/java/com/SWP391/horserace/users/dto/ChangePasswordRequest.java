package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service change-password payload for the authenticated user.
 *
 * <p>The user must prove ownership by supplying their {@code currentPassword}; {@code newPassword}
 * must meet the same strength rules used elsewhere (8+ chars, at least one digit and one symbol —
 * see {@code PasswordResetServiceImpl}) and match {@code confirmPassword}. Equality of the new
 * password with the current one is rejected in the service layer.
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Password must contain at least one number and one special character")
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {
}
