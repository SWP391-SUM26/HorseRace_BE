package com.SWP391.horserace.roles.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body for PUT /api/v1/roles/{roleId}/permissions — replaces the whole set. */
public record UpdateRolePermissionsRequest(@NotNull List<String> permissionCodes) {
}
