package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Resend-code payload — the email for which to resend the 6-digit reset code. */
public record ResendCodeRequest(
        @NotBlank @Email String email) {
}
