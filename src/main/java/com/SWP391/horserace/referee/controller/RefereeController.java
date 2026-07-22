package com.SWP391.horserace.referee.controller;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.dto.RefereeDashboardResponse;
import com.SWP391.horserace.referee.service.RefereeDashboardService;
import com.SWP391.horserace.referee.service.RefereeService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referee")
@RequiredArgsConstructor
public class RefereeController {

    private final RefereeService refereeService;
    private final RefereeDashboardService refereeDashboardService;

    /** GET /api/v1/referee/dashboard — aggregate for screen 1 (optional {@code ?raceId=}). */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<RefereeDashboardResponse> getDashboard(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "raceId", required = false) UUID raceId) {
        return ApiResponse.<RefereeDashboardResponse>builder()
                .success(true)
                .message("Fetched referee dashboard")
                .data(refereeDashboardService.getDashboard(userId, raceId))
                .build();
    }

    /** POST /api/v1/referee/horses/{horseId}/health-check — record a horse health check. */
    @PostMapping("/horses/{horseId}/health-check")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<MedicalStatusResponse> recordHealthCheck(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID horseId,
            @Valid @RequestBody HealthCheckRequest request) {
        return ApiResponse.<MedicalStatusResponse>builder()
                .success(true)
                .message("Health check recorded")
                .data(refereeService.recordHealthCheck(userId, horseId, request))
                .build();
    }

}
