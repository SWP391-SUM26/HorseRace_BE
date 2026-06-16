package com.SWP391.horserace.staffing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for the Figma race-assignment table row.
 * Each row shows a race and its current referee assignment (if any).
 */
@Data
@Builder
public class RaceAssignmentResponse {

    // -- race info --
    private UUID raceId;
    private String raceName;
    private String raceCode;
    private String raceStatus;
    private OffsetDateTime scheduledStartAt;

    // -- referee assignment info (null when unassigned) --
    private UUID refAssignmentId;
    private UUID refereeUserId;
    private String refereeName;
    private String refereeAvatarUrl;
    private String panelRole;
    private String assignmentStatus;
}
