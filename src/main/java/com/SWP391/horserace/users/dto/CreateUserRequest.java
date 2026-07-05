package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/users} — admin provisions a new ACTIVE user with a given role.
 *
 * <p>No password field: the server generates a strong random password, stores it bcrypt-hashed,
 * and emails it to the new user. Admins cannot provision another ADMIN account.
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
        String phone
) {
}
