package com.SWP391.horserace.assignments.controller;

import com.SWP391.horserace.assignments.dto.JockeyRideResponse;
import com.SWP391.horserace.assignments.service.JockeyAssignmentService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Jockey-self ride schedule endpoint (FE-v2 jockey contract #6).
 *
 * <p>Lives outside {@code /assignments/invitations} because its path is
 * {@code /api/v1/assignments/me/rides}.
 */
@RestController
@RequestMapping("/api/v1/assignments/me")
@RequiredArgsConstructor
public class JockeyRidesController {

    private final JockeyAssignmentService assignmentService;

    /**
     * GET /api/v1/assignments/me/rides?when=PAST|UPCOMING — the caller's ACCEPTED rides
     * split by time window (defaults to UPCOMING).
     */
    @GetMapping("/rides")
    public ApiResponse<List<JockeyRideResponse>> getMyRides(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "when", required = false) String when) {
        return ApiResponse.<List<JockeyRideResponse>>builder()
                .success(true)
                .message("Fetched rides")
                .data(assignmentService.getMyRides(userId, when))
                .build();
    }
}
