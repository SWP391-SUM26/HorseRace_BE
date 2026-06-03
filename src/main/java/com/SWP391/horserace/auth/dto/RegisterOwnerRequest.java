package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for horse-owner self-registration.
 * Maps to the "Owner Registration" form in the Figma design.
 *
 * <p>File upload (stable logo / avatar) is not yet supported — pass a URL string for
 * {@code avatarUrl} if available, or leave it null.</p>
 */
public record RegisterOwnerRequest(

        @NotBlank(message = "Full legal name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        /** Optional contact / phone number. */
        @Pattern(regexp = "^$|^(0|\\+84|84)[35789][0-9]{8}$", message = "INVALID_PHONE_FORMAT")
        String contactNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "INVALID_PASSWORD")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).*$", message = "WEAK_PASSWORD")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,

        /** Optional region / location (e.g. "Kentucky, USA"). */
        @Size(max = 100, message = "Primary region must not exceed 100 characters")
        String primaryRegion,

        /** Optional stable name (e.g. "Sterling Racing Stables"). */
        @Size(max = 100, message = "Stable name must not exceed 100 characters")
        String stableName,

        /** Optional professional bio / credentials. */
        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        /** Optional avatar / stable logo URL (file upload handled separately). */
        String avatarUrl,
        
        @NotNull(message = "You must agree to the Terms of Service and confirm ownership credentials")
        @AssertTrue(message = "You must agree to the Terms of Service and confirm ownership credentials")
        Boolean agreedToTerms
) {
}
