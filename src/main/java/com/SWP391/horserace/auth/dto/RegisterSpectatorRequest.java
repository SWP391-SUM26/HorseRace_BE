package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

/**
 * Payload for spectator self-registration.
 * Maps to the "Create Spectator Account" form in the Figma design.
 */
public record RegisterSpectatorRequest(

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        /** Optional — user may omit phone number. */
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,
        @NotNull(message = "You must agree to the Terms of Service and Privacy Policy")
        @AssertTrue(message = "You must agree to the Terms of Service and Privacy Policy")
        Boolean agreedToTerms
) {
}
