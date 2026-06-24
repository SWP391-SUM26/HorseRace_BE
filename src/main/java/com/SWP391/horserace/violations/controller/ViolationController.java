package com.SWP391.horserace.violations.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.violations.dto.RulingRequest;
import com.SWP391.horserace.violations.dto.RulingResponse;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.violations.service.ViolationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Top-level violation operations (FE-v2 §3): detail, ruling, CSV export. */
@RestController
@RequestMapping("/api/v1/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;

    /** GET /{id} — full violation detail incl. ruling, regulatoryText, footageUrl. */
    @GetMapping("/{violationId}")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<ViolationDetailResponse> get(@PathVariable UUID violationId) {
        return ApiResponse.<ViolationDetailResponse>builder()
                .success(true)
                .message("Fetched violation")
                .data(violationService.getViolation(violationId))
                .build();
    }

    /** PATCH /{id}/ruling — record the official ruling; may create a penalty + resolve/dismiss. */
    @PatchMapping("/{violationId}/ruling")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<RulingResponse> rule(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID violationId,
            @Valid @RequestBody RulingRequest request) {
        return ApiResponse.<RulingResponse>builder()
                .success(true)
                .message("Ruling recorded")
                .data(violationService.recordRuling(userId, violationId, request))
                .build();
    }

    /** GET /export?raceId=&format=csv — stream a CSV of the race's violations (not ApiResponse-wrapped). */
    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ResponseEntity<String> export(
            @RequestParam("raceId") UUID raceId,
            @RequestParam(name = "format", required = false, defaultValue = "csv") String format) {
        String csv = violationService.exportCsv(raceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"violations-" + raceId + ".csv\"")
                .body(csv);
    }
}
