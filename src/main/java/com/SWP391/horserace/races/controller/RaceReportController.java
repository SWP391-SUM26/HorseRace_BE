package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.SubmitReportRequest;
import com.SWP391.horserace.races.dto.SubmitReportResponse;
import com.SWP391.horserace.races.service.RaceResultService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.staffing.service.RefereeSubmissionCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CN3 — the emailed-OTP referee report flow: request a one-time code, publish the combined
 * results+violations report unlocked by that code, and the ADMIN override escape hatch.
 */
@RestController
@RequestMapping("/api/v1/races/{raceId}")
@RequiredArgsConstructor
public class RaceReportController {

    private final RaceResultService raceResultService;
    private final RefereeSubmissionCodeService refereeSubmissionCodeService;

    /** POST /referee-code/request — email the referee a one-time submission code. */
    @PostMapping("/referee-code/request")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<Void> requestRefereeCode(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId) {
        refereeSubmissionCodeService.requestCode(userId, raceId);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Submission code sent to your verified email")
                .build();
    }

    /** POST /report — publish the combined results+violations report, unlocked by the OTP. */
    @PostMapping("/report")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<SubmitReportResponse> submitReport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody SubmitReportRequest request) {
        return ApiResponse.<SubmitReportResponse>builder()
                .success(true)
                .message("Report submitted")
                .data(raceResultService.submitReport(userId, raceId, request))
                .build();
    }

    /** POST /report/admin-override — ADMIN publishes the prepared report WITHOUT an OTP (Risk a/c). */
    @PostMapping("/report/admin-override")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SubmitReportResponse> adminOverridePublish(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody SubmitReportRequest request) {
        return ApiResponse.<SubmitReportResponse>builder()
                .success(true)
                .message("Report published (admin override)")
                .data(raceResultService.adminOverridePublish(userId, raceId, request))
                .build();
    }
}
