package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Exchange a refresh token for a new access token (and a rotated refresh token). */
public record RefreshRequest(
        @NotBlank String refreshToken) {
}
