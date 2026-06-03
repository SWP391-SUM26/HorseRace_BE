package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile update payload. A user may change only their own display fields —
 * {@code fullName}, {@code phone} and {@code avatarUrl}. Identity/security fields (email,
 * password) and admin-controlled fields (role, status, kyc) are intentionally excluded.
 *
 * <p>All fields are optional: only the non-null ones are applied, so the client can send a
 * partial payload. Validation mirrors the {@code app_user} column constraints.
 */
public record UpdateProfileRequest(

        @Size(max = 255, message = "Full name must not exceed 255 characters")
        String fullName,

        @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,30}$", message = "Phone number is invalid")
        String phone,

        @Size(max = 2048, message = "Avatar URL is too long")
        String avatarUrl
) {
}
