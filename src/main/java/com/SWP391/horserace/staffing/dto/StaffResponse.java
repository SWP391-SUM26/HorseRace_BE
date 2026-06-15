package com.SWP391.horserace.staffing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API view of a staff member (referee). Never expose password_hash.
 */
@Data
@Builder
public class StaffResponse {
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

    /** Number of races currently assigned (non-REVOKED). */
    private long assignedRaceCount;
}
