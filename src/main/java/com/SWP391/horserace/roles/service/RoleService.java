package com.SWP391.horserace.roles.service;

import com.SWP391.horserace.roles.dto.PermissionResponse;
import com.SWP391.horserace.roles.dto.RoleResponse;

import java.util.List;
import java.util.UUID;

/**
 * Role → permission administration ("phân quyền cho các role").
 *
 * <p><b>Scope note:</b> authorization today is enforced by {@code @PreAuthorize("hasRole('X')")}
 * against the JWT role claim; nothing checks individual permission codes. So editing this matrix
 * changes what the UI advertises and what {@code /users/{id}/permissions} reports, but does not by
 * itself grant or revoke server-side access. Moving the gates to {@code hasAuthority(...)} is a
 * separate change.
 */
public interface RoleService {

    List<RoleResponse> listRoles();

    List<PermissionResponse> listPermissions();

    /** Replaces a role's whole permission set. Unknown codes are rejected, not skipped. */
    RoleResponse replacePermissions(UUID roleId, List<String> permissionCodes);
}
