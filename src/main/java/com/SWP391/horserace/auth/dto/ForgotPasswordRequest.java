package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Forgot-password payload — just the registered email. */
public record ForgotPasswordRequest(
        @NotBlank @Email String email) {
}
