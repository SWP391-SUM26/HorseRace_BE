package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Revoke a refresh token (logout). */
public record LogoutRequest(
        @NotBlank String refreshToken) {
}
