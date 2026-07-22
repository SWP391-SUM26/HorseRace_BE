package com.SWP391.horserace.roles.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** One permission in the catalogue. */
@Data
@Builder
public class PermissionResponse {
    private UUID permissionId;
    private String code;
    private String description;
}
