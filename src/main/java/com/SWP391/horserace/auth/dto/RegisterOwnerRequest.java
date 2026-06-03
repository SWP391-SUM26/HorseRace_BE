package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

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
        String contactNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,

        /** Optional region / location (e.g. "Kentucky, USA"). */
        String primaryRegion,

        /** Optional stable name (e.g. "Sterling Racing Stables"). */
        String stableName,

        /** Optional professional bio / credentials. */
        String bio,

        /** Optional avatar / stable logo URL (file upload handled separately). */
        String avatarUrl,
        
        @NotNull(message = "You must agree to the Terms of Service and confirm ownership credentials")
        @AssertTrue(message = "You must agree to the Terms of Service and confirm ownership credentials")
        Boolean agreedToTerms
) {
}
