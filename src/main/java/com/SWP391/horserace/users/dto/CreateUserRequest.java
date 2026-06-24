package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/users} — admin provisions a new ACTIVE user with a given role.
 *
 * <p>{@code tempPassword} is optional; when omitted the account is created with a default
 * temporary password. Passwords are stored {@code {noop}}-prefixed (matching the seeded users)
 * so they can be rotated later.
 */
public record CreateUserRequest(
        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "Full name must not exceed 255 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "Email is too long")
        String email,

        @NotBlank(message = "roleCode is required")
        String roleCode,

        @Pattern(regexp = "^\\+?[0-9\\-\\s]{7,30}$", message = "Phone number is invalid")
        String phone,

        String tempPassword
) {
}
