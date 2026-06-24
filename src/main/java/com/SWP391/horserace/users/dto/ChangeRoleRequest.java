package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code PATCH /api/v1/users/{id}/role} — assign a user's role by role code.
 */
public record ChangeRoleRequest(
        @NotBlank(message = "roleCode is required")
        String roleCode
) {
}
