package com.SWP391.horserace.staffing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Dashboard statistics for the Staffing Management page header cards.
 */
@Data
@Builder
public class StaffingDashboardResponse {

    /** Total number of scheduled races (status = SCHEDULED). */
    private long totalScheduledRaces;

    /** Number of scheduled races that have at least one non-REVOKED assignment. */
    private long assignedReferees;

    /** Number of scheduled races with no active assignment. */
    private long unassignedRaces;

    /** Number of RACE_REFEREE users with ACTIVE status. */
    private long availableReferees;
}
