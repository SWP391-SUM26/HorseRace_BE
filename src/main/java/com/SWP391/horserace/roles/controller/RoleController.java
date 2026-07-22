package com.SWP391.horserace.roles.controller;

import com.SWP391.horserace.roles.dto.PermissionResponse;
import com.SWP391.horserace.roles.dto.RoleResponse;
import com.SWP391.horserace.roles.dto.UpdateRolePermissionsRequest;
import com.SWP391.horserace.roles.service.RoleService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Role / permission administration ("phân quyền cho các role").
 *
 * <p>The {@code roles} module previously had only an entity and a repository — the role→permission
 * matrix was seed-data only, with no way to inspect or change it through the API.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** GET /roles — every role with its granted permission codes and how many users hold it. */
    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .success(true)
                .message("Fetched roles")
                .data(roleService.listRoles())
                .build();
    }

    /** GET /permissions — the full permission catalogue (columns of the matrix). */
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.<List<PermissionResponse>>builder()
                .success(true)
                .message("Fetched permissions")
                .data(roleService.listPermissions())
                .build();
    }

    /** PUT /roles/{roleId}/permissions — replace a role's whole permission set. */
    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleResponse> replacePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .success(true)
                .message("Role permissions updated")
                .data(roleService.replacePermissions(roleId, request.permissionCodes()))
                .build();
    }
}
