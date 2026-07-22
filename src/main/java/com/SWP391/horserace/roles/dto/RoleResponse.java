package com.SWP391.horserace.roles.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** A role plus the permission codes currently granted to it. */
@Data
@Builder
public class RoleResponse {
    private UUID roleId;
    private String roleCode;
    private String roleName;
    private String description;
    private String status;
    private long userCount;
    private List<String> permissionCodes;
}
