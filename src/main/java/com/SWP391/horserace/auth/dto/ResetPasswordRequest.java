package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reset-password payload — email + 6-digit code + new password + confirmation. */
public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String code,
        @NotBlank @Size(min = 8, message = "INVALID_PASSWORD") String newPassword,
        @NotBlank String confirmPassword) {
}
