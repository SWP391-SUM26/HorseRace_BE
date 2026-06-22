package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Verify-email payload — account email + the 6-digit verification code. */
public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String code) {
}
