package com.SWP391.horserace.staffing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query-parameter DTO for {@code GET /api/v1/staffing/assignments}.
 * Mirrors the Figma table filters: search, race status, assignment status, pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceAssignmentFilterRequest {

    /** Search by race name, referee name, or race code (case-insensitive LIKE). */
    private String search;

    /** Filter by race status: SCHEDULED, OPEN, CLOSED, RUNNING, etc. */
    private String raceStatus;

    /** Filter by assignment status: ASSIGNED, UNASSIGNED, ALL. */
    private String assignmentStatus;

    // -- pagination --
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    // -- sorting --
    @Builder.Default
    private String sortBy = "scheduledStartAt";

    @Builder.Default
    private String sortDir = "asc";
}
