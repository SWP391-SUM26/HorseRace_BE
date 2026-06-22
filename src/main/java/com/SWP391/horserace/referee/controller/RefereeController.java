package com.SWP391.horserace.referee.controller;

import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.referee.dto.CreateReportRequest;
import com.SWP391.horserace.referee.dto.HealthCheckRequest;
import com.SWP391.horserace.referee.dto.ReportFilterRequest;
import com.SWP391.horserace.referee.dto.ReportResponse;
import com.SWP391.horserace.referee.dto.UpdateReportRequest;
import com.SWP391.horserace.referee.service.RefereeService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/referee")
@RequiredArgsConstructor
public class RefereeController {

    private final RefereeService refereeService;

    /** POST /api/v1/referee/horses/{horseId}/health-check — record a horse health check. */
    @PostMapping("/horses/{horseId}/health-check")
    @ResponseStatus(HttpStatus.CREATED)
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

    /** POST /api/v1/referee/reports — file a referee report (incident / violation / objection / general). */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> createReport(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.<ReportResponse>builder()
                .success(true)
                .message("Report created")
                .data(refereeService.createReport(userId, request))
                .build();
    }

    /** GET /api/v1/referee/reports — list reports with filters, sort and pagination. */
    @GetMapping("/reports")
    public ApiResponse<Page<ReportResponse>> listReports(@ModelAttribute ReportFilterRequest filter) {
        return ApiResponse.<Page<ReportResponse>>builder()
                .success(true)
                .message("Fetched reports")
                .data(refereeService.listReports(filter))
                .build();
    }

    /** PUT /api/v1/referee/reports/{id} — partial update of a DRAFT report. */
    @PutMapping("/reports/{id}")
    public ApiResponse<ReportResponse> updateReport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReportRequest request) {
        return ApiResponse.<ReportResponse>builder()
                .success(true)
                .message("Report updated")
                .data(refereeService.updateReport(userId, id, request))
                .build();
    }

    /** PATCH /api/v1/referee/reports/{id}/submit — submit a DRAFT report officially. */
    @PatchMapping("/reports/{id}/submit")
    public ApiResponse<ReportResponse> submitReport(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.<ReportResponse>builder()
                .success(true)
                .message("Report submitted")
                .data(refereeService.submitReport(userId, id))
                .build();
    }
}
