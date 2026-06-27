package com.SWP391.horserace.staffing.service;

import com.SWP391.horserace.staffing.dto.AssignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentFilterRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentResponse;
import com.SWP391.horserace.staffing.dto.ReassignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RefereeAssignmentResponse;
import com.SWP391.horserace.staffing.dto.StaffingDashboardResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface StaffRefereeAssignmentService {

    /** Dashboard statistics (Figma header cards). */
    StaffingDashboardResponse getDashboard();

    /** List race assignments with filters and pagination (Figma table). */
    Page<RaceAssignmentResponse> getRaceAssignments(RaceAssignmentFilterRequest filter);

    /** Full referee panel (all non-revoked assignments) for a single race. */
    java.util.List<RefereeAssignmentResponse> getRacePanel(UUID raceId);

    /** Distinct race IDs the given referee is actively assigned to officiate. */
    java.util.List<UUID> getAssignedRaceIds(UUID refereeUserId);

    /** The signed-in referee's own active assignments (including their per-race code). */
    java.util.List<RefereeAssignmentResponse> getMyAssignments(UUID refereeUserId);

    /** Assign a referee to a race (task 148). */
    RefereeAssignmentResponse assignReferee(AssignRefereeRequest request, UUID currentUserId);

    /** Reassign a different referee to an existing assignment (task 150). */
    RefereeAssignmentResponse reassignReferee(UUID refAssignmentId, ReassignRefereeRequest request, UUID currentUserId);

    /** Remove (revoke) a referee assignment (task 152). */
    void removeAssignment(UUID refAssignmentId);
}
