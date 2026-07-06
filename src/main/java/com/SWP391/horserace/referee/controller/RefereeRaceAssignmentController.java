package com.SWP391.horserace.referee.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.staffing.dto.DeclineAssignmentRequest;
import com.SWP391.horserace.staffing.dto.RefereeAssignmentResponse;
import com.SWP391.horserace.staffing.service.StaffRefereeAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Referee-facing inbox for PER-RACE officiating assignments (CN1): list my assignments, accept
 * (→CONFIRMED) or decline (→DECLINED). Distinct from tournament invitations
 * ({@code /api/v1/referee/invitations}). All endpoints act on the signed-in referee only.
 */
@RestController
@RequestMapping("/api/v1/referee/race-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RACE_REFEREE')")
public class RefereeRaceAssignmentController {

    private final StaffRefereeAssignmentService assignmentService;

    /** GET /api/v1/referee/race-assignments — the caller's own race assignments. */
    @GetMapping
    public ApiResponse<List<RefereeAssignmentResponse>> myAssignments(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<RefereeAssignmentResponse>>builder()
                .success(true)
                .message("Fetched my race assignments")
                .data(assignmentService.getMyRaceAssignments(userId))
                .build();
    }

    /** PATCH /api/v1/referee/race-assignments/{id}/accept → CONFIRMED. */
    @PatchMapping("/{id}/accept")
    public ApiResponse<RefereeAssignmentResponse> accept(@AuthenticationPrincipal UUID userId,
                                                         @PathVariable UUID id) {
        return ApiResponse.<RefereeAssignmentResponse>builder()
                .success(true)
                .message("Assignment accepted")
                .data(assignmentService.acceptAssignment(userId, id))
                .build();
    }

    /** PATCH /api/v1/referee/race-assignments/{id}/decline → DECLINED (notifies the assigning admin). */
    @PatchMapping("/{id}/decline")
    public ApiResponse<RefereeAssignmentResponse> decline(@AuthenticationPrincipal UUID userId,
                                                          @PathVariable UUID id,
                                                          @Valid @RequestBody(required = false) DeclineAssignmentRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ApiResponse.<RefereeAssignmentResponse>builder()
                .success(true)
                .message("Assignment declined")
                .data(assignmentService.declineAssignment(userId, id, reason))
                .build();
    }
}
