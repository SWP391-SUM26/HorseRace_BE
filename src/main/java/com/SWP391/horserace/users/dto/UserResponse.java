package com.SWP391.horserace.users.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API view of a user. Never expose password_hash or soft-delete internals.
 */
@Data
@Builder
public class UserResponse {
    private UUID userId;
    private String userCode;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String status;
    private String kycStatus;
    private String roleCode;
    private String roleName;
    private OffsetDateTime createdAt;
}
