package com.SWP391.horserace.inspections.controller;

import com.SWP391.horserace.inspections.dto.InspectionListItemResponse;
import com.SWP391.horserace.inspections.dto.InspectionRequest;
import com.SWP391.horserace.inspections.dto.InspectionResponse;
import com.SWP391.horserace.inspections.dto.SubmitAllRequest;
import com.SWP391.horserace.inspections.dto.SubmitAllResponse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;
import com.SWP391.horserace.inspections.service.InspectionService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Pre-race inspection per entry (FE-v2 §2). */
@RestController
@RequestMapping("/api/v1/races/{raceId}/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    /** POST — create/upsert the inspection for an entry; stamps the calling steward. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<InspectionResponse> upsert(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody InspectionRequest request) {
        return ApiResponse.<InspectionResponse>builder()
                .success(true)
                .message("Inspection recorded")
                .data(inspectionService.upsertInspection(userId, raceId, request))
                .build();
    }

    /** GET — all entries of the race merged with their inspection (optionally filtered by status). */
    @GetMapping
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<List<InspectionListItemResponse>> list(
            @PathVariable UUID raceId,
            @RequestParam(name = "status", required = false) InspectionStatus status) {
        return ApiResponse.<List<InspectionListItemResponse>>builder()
                .success(true)
                .message("Fetched inspections")
                .data(inspectionService.listInspections(raceId, status))
                .build();
    }

    /** PATCH /submit-all — submit CLEARED inspections; report non-CLEARED entries as blocked. */
    @PatchMapping("/submit-all")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<SubmitAllResponse> submitAll(
            @PathVariable UUID raceId,
            @RequestBody SubmitAllRequest request) {
        return ApiResponse.<SubmitAllResponse>builder()
                .success(true)
                .message("Inspections submitted")
                .data(inspectionService.submitAll(raceId, request))
                .build();
    }
}
