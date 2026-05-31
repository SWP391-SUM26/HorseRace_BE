package com.SWP391.horserace.auth.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record RegisterResponse(
        UUID userId,
        String userCode,
        String fullName,
        String email,
        String role
) {
}