package com.SWP391.horserace.onboarding.controller;

import com.SWP391.horserace.onboarding.dto.ApplicationDetail;
import com.SWP391.horserace.onboarding.dto.ApplicationSummary;
import com.SWP391.horserace.onboarding.dto.OnboardingStatsResponse;
import com.SWP391.horserace.onboarding.dto.RejectRequest;
import com.SWP391.horserace.onboarding.dto.RequestInfoRequest;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.SWP391.horserace.onboarding.service.RefereeApplicationService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Referee Applicant Onboarding — Registration Approval (FE-v2 PATH B).
 * Every endpoint is gated to RACE_REFEREE.
 */
@RestController
@RequestMapping("/api/v1/referee/applications")
@RequiredArgsConstructor
public class RefereeApplicationController {

    private final RefereeApplicationService applicationService;

    /** GET — filter status/requestedRole/q + paginate. */
    @GetMapping
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<Page<ApplicationSummary>> list(
            @RequestParam(name = "status", required = false) ApplicationStatus status,
            @RequestParam(name = "requestedRole", required = false) RequestedRole requestedRole,
            @RequestParam(name = "q", required = false) String q,
            Pageable pageable) {
        return ApiResponse.<Page<ApplicationSummary>>builder()
                .success(true)
                .message("Fetched applications")
                .data(applicationService.list(status, requestedRole, q, pageable))
                .build();
    }

    /** GET — today-scoped onboarding stats. */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<OnboardingStatsResponse> stats() {
        return ApiResponse.<OnboardingStatsResponse>builder()
                .success(true)
                .message("Fetched onboarding stats")
                .data(applicationService.stats())
                .build();
    }

    /** GET — full dossier. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<ApplicationDetail> detail(@PathVariable("id") UUID id) {
        return ApiResponse.<ApplicationDetail>builder()
                .success(true)
                .message("Fetched application")
                .data(applicationService.getDetail(id))
                .build();
    }

    /** PATCH — approve & onboard (creates/activates the account). */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<ApplicationDetail> approve(
            @AuthenticationPrincipal UUID reviewerUserId,
            @PathVariable("id") UUID id) {
        return ApiResponse.<ApplicationDetail>builder()
                .success(true)
                .message("Application approved")
                .data(applicationService.approve(id, reviewerUserId))
                .build();
    }

    /** PATCH — reject with a reason. */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<ApplicationDetail> reject(
            @AuthenticationPrincipal UUID reviewerUserId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectRequest request) {
        return ApiResponse.<ApplicationDetail>builder()
                .success(true)
                .message("Application rejected")
                .data(applicationService.reject(id, request.getReason(), reviewerUserId))
                .build();
    }

    /** PATCH — request more info. */
    @PatchMapping("/{id}/request-info")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<ApplicationDetail> requestInfo(
            @AuthenticationPrincipal UUID reviewerUserId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody RequestInfoRequest request) {
        return ApiResponse.<ApplicationDetail>builder()
                .success(true)
                .message("Additional information requested")
                .data(applicationService.requestInfo(id, request.getNote(), reviewerUserId))
                .build();
    }

    /** GET — previous applications by the same applicant. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<List<ApplicationSummary>> history(@PathVariable("id") UUID id) {
        return ApiResponse.<List<ApplicationSummary>>builder()
                .success(true)
                .message("Fetched application history")
                .data(applicationService.history(id))
                .build();
    }

    /** GET — full dossier export (STUB). */
    @GetMapping("/{id}/dossier")
    @PreAuthorize("hasRole('RACE_REFEREE')")
    public ApiResponse<Void> dossier(@PathVariable("id") UUID id) {
        // Validate the application exists so the stub still 404s for bad ids.
        applicationService.getDetail(id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Dossier export not yet implemented")
                .build();
    }
}
