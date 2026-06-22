package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request-email-verification payload — just the account email. */
public record RequestEmailVerificationRequest(
        @NotBlank @Email String email) {
}
