package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code PATCH /api/v1/users/{id}/status} — suspend / activate / ban a user.
 * {@code reason} is optional and informational (no dedicated column today; it is logged).
 */
public record ChangeStatusRequest(
        @NotBlank(message = "status is required")
        String status,

        String reason
) {
}
