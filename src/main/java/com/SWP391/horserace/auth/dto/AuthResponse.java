package com.SWP391.horserace.auth.dto;

import lombok.Builder;

import java.util.UUID;

/** Token bundle returned by login / refresh / google sign-in. */
@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,      // always "Bearer"
        long expiresInSeconds, // access-token lifetime
        UUID userId,
        String email,
        String role) {
}
