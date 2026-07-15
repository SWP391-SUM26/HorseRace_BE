package com.SWP391.horserace.staffing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single referee assignment detail.
 */
@Data
@Builder
public class RefereeAssignmentResponse {

    private UUID refAssignmentId;

    // -- race --
    private UUID raceId;
    private String raceName;
    private String raceCode;
    private OffsetDateTime scheduledStartAt;

    // -- referee --
    private UUID refereeUserId;
    private String refereeName;
    private String refereeAvatarUrl;

    // -- assignment --
    private String panelRole;
    /** Per-race code the referee must quote when filing results/violations. */
    private String refCode;
    private String status;
    private OffsetDateTime assignedAt;
    /** When the referee accepted/declined (CN1); null while still ASSIGNED. */
    private OffsetDateTime respondedAt;
    /** Reason given on decline (CN1). */
    private String declineReason;
    private OffsetDateTime createdAt;

    // -- created by --
    private UUID createdByUserId;
    private String createdByName;
}
