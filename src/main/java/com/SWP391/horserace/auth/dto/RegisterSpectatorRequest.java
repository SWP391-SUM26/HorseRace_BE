package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

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
        @Pattern(regexp = "^$|^(0|\\+84|84)[35789][0-9]{8}$", message = "INVALID_PHONE_FORMAT")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "INVALID_PASSWORD")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).*$", message = "WEAK_PASSWORD")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,
        @NotNull(message = "You must agree to the Terms of Service and Privacy Policy")
        @AssertTrue(message = "You must agree to the Terms of Service and Privacy Policy")
        Boolean agreedToTerms
) {
}
