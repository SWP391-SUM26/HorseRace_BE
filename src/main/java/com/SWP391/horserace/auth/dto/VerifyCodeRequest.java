package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Verify-code payload — email + 6-digit code (without changing password yet). */
public record VerifyCodeRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String code) {
}
