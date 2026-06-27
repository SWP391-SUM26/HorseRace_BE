package com.SWP391.horserace.violations.controller;

import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.violations.dto.ViolationListItemResponse;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import com.SWP391.horserace.violations.service.ViolationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Race-scoped violations (FE-v2 §3): create + list. */
@RestController
@RequestMapping("/api/v1/races/{raceId}/violations")
@RequiredArgsConstructor
public class RaceViolationController {

    private final ViolationService violationService;

    /** POST — log a new violation for the race; stamps the calling referee as reporter. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<ViolationDetailResponse> create(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody CreateViolationRequest request) {
        return ApiResponse.<ViolationDetailResponse>builder()
                .success(true)
                .message("Violation logged")
                .data(violationService.createViolation(userId, raceId, request))
                .build();
    }

    /** GET — all violations of the race, optionally filtered by status / severity / infractionType.
     *  Readable by referees/admins and by horse owners (to view the report of races they ran in). */
    @GetMapping
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN','HORSE_OWNER')")
    public ApiResponse<List<ViolationListItemResponse>> list(
            @PathVariable UUID raceId,
            @RequestParam(name = "status", required = false) ViolationStatus status,
            @RequestParam(name = "severity", required = false) SeverityLevel severity,
            @RequestParam(name = "infractionType", required = false) InfractionType infractionType) {
        return ApiResponse.<List<ViolationListItemResponse>>builder()
                .success(true)
                .message("Fetched violations")
                .data(violationService.listViolations(raceId, status, severity, infractionType))
                .build();
    }
}
