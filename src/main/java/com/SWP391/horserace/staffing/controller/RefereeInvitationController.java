package com.SWP391.horserace.staffing.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.staffing.dto.TournamentRefereeAssignmentResponse;
import com.SWP391.horserace.staffing.service.TournamentRefereeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Referee-facing inbox for tournament invitations (accept/decline) — §E3. */
@RestController
@RequestMapping("/api/v1/referee/invitations")
@RequiredArgsConstructor
public class RefereeInvitationController {

    private final TournamentRefereeService tournamentRefereeService;

    /** GET — the signed-in referee's pending tournament invitations. */
    @GetMapping
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<List<TournamentRefereeAssignmentResponse>> myInvitations(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<TournamentRefereeAssignmentResponse>>builder()
                .success(true)
                .message("Fetched tournament invitations")
                .data(tournamentRefereeService.listMyInvitations(userId))
                .build();
    }

    /** PATCH /{id}/accept — accept a pending invitation. */
    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<TournamentRefereeAssignmentResponse> accept(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ApiResponse.<TournamentRefereeAssignmentResponse>builder()
                .success(true)
                .message("Invitation accepted")
                .data(tournamentRefereeService.accept(id, userId))
                .build();
    }

    /** PATCH /{id}/reject — decline a pending invitation. */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<TournamentRefereeAssignmentResponse> reject(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ApiResponse.<TournamentRefereeAssignmentResponse>builder()
                .success(true)
                .message("Invitation declined")
                .data(tournamentRefereeService.reject(id, userId))
                .build();
    }
}
