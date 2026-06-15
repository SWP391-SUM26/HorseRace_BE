package com.SWP391.horserace.staffing.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.staffing.dto.AssignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentFilterRequest;
import com.SWP391.horserace.staffing.dto.RaceAssignmentResponse;
import com.SWP391.horserace.staffing.dto.ReassignRefereeRequest;
import com.SWP391.horserace.staffing.dto.RefereeAssignmentResponse;
import com.SWP391.horserace.staffing.dto.StaffingDashboardResponse;
import com.SWP391.horserace.staffing.service.StaffRefereeAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for referee assignment management.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code GET    /api/v1/staffing/dashboard}       — dashboard statistics</li>
 *   <li>{@code GET    /api/v1/staffing/assignments}      — list race assignments (Figma table)</li>
 *   <li>{@code POST   /api/v1/staffing/assignments}      — assign referee to race (task 148)</li>
 *   <li>{@code PUT    /api/v1/staffing/assignments/{id}} — reassign referee (task 150)</li>
 *   <li>{@code DELETE /api/v1/staffing/assignments/{id}} — remove assignment (task 152)</li>
 * </ul>
 *
 * <p><b>DEV mode:</b> accepts {@code currentUserId} query parameter as JWT fallback.
 */
@RestController
@RequestMapping("/api/v1/staffing")
@RequiredArgsConstructor
public class RefereeAssignmentController {

    private final StaffRefereeAssignmentService assignmentService;

    // -------------------------------------------------------------------------
    // GET /api/v1/staffing/dashboard — Dashboard Statistics
    // -------------------------------------------------------------------------
    @GetMapping("/dashboard")
    public ApiResponse<StaffingDashboardResponse> getDashboard() {
        return ApiResponse.<StaffingDashboardResponse>builder()
                .success(true)
                .message("Fetched staffing dashboard")
                .data(assignmentService.getDashboard())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/staffing/assignments — List Race Assignments
    // -------------------------------------------------------------------------
    /**
     * List race assignments with optional search, filter, and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code search}            — search by race name, referee name, or race code</li>
     *   <li>{@code raceStatus}        — SCHEDULED, OPEN, CLOSED, RUNNING, etc.</li>
     *   <li>{@code assignmentStatus}  — ASSIGNED, UNASSIGNED</li>
     *   <li>{@code page}              — page number (0-indexed, default 0)</li>
     *   <li>{@code size}              — page size (default 10)</li>
     *   <li>{@code sortBy}            — scheduledStartAt (default)</li>
     *   <li>{@code sortDir}           — asc (default) / desc</li>
     * </ul>
     */
    @GetMapping("/assignments")
    public ApiResponse<Page<RaceAssignmentResponse>> getRaceAssignments(
            @ModelAttribute RaceAssignmentFilterRequest filter) {

        return ApiResponse.<Page<RaceAssignmentResponse>>builder()
                .success(true)
                .message("Fetched race assignments")
                .data(assignmentService.getRaceAssignments(filter))
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/staffing/assignments — Assign Referee
    // -------------------------------------------------------------------------
    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefereeAssignmentResponse> assignReferee(
            @Valid @RequestBody AssignRefereeRequest request,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);

        return ApiResponse.<RefereeAssignmentResponse>builder()
                .success(true)
                .message("Referee assigned successfully")
                .data(assignmentService.assignReferee(request, currentUserId))
                .build();
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/staffing/assignments/{id} — Reassign Referee
    // -------------------------------------------------------------------------
    @PutMapping("/assignments/{id}")
    public ApiResponse<RefereeAssignmentResponse> reassignReferee(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignRefereeRequest request,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);

        return ApiResponse.<RefereeAssignmentResponse>builder()
                .success(true)
                .message("Referee reassigned successfully")
                .data(assignmentService.reassignReferee(id, request, currentUserId))
                .build();
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/staffing/assignments/{id} — Remove Assignment
    // -------------------------------------------------------------------------
    @DeleteMapping("/assignments/{id}")
    public ApiResponse<Void> removeAssignment(@PathVariable UUID id) {
        assignmentService.removeAssignment(id);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Assignment removed successfully")
                .build();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Resolves the current user id from either:
     * <ol>
     *   <li>The JWT authentication principal (production path), or</li>
     *   <li>The {@code currentUserId} query parameter (DEV/Swagger fallback).</li>
     * </ol>
     */
    private UUID resolveUserId(Authentication authentication, UUID fallbackUserId) {
        if (authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
                // not a valid UUID — fall through
            }
        }
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        return null;
    }
}
