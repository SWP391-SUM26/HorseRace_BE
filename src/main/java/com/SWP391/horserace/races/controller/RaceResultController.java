package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.CertifyResultsRequest;
import com.SWP391.horserace.races.dto.CertifyResultsResponse;
import com.SWP391.horserace.races.dto.RaceResultsResponse;
import com.SWP391.horserace.races.dto.RecordResultsRequest;
import com.SWP391.horserace.races.dto.ResultRowResponse;
import com.SWP391.horserace.races.dto.UpdateResultRequest;
import com.SWP391.horserace.races.dto.UpdateResultResponse;
import com.SWP391.horserace.races.service.RaceResultService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Results record/read/edit/certify for a race (FE-v2 §5). */
@RestController
@RequestMapping("/api/v1/races/{raceId}/results")
@RequiredArgsConstructor
public class RaceResultController {

    private final RaceResultService raceResultService;

    /** POST — bulk upsert the finish order (one result per entry, status PROVISIONAL). */
    @PostMapping
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<List<ResultRowResponse>> recordResults(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody RecordResultsRequest request) {
        return ApiResponse.<List<ResultRowResponse>>builder()
                .success(true)
                .message("Results recorded")
                .data(raceResultService.recordResults(userId, raceId, request))
                .build();
    }

    /**
     * GET — the full result sheet for a race, ordered by finish position. Readable by anyone
     * (owners, jockeys, spectators all view results) — only recording/editing/certifying is gated.
     */
    @GetMapping
    public ApiResponse<RaceResultsResponse> getResults(@PathVariable UUID raceId) {
        return ApiResponse.<RaceResultsResponse>builder()
                .success(true)
                .message("Fetched results")
                .data(raceResultService.getResults(raceId))
                .build();
    }

    /** PATCH /certify — flip all results + the race to OFFICIAL (FE-v2 §5). */
    @PatchMapping("/certify")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CertifyResultsResponse> certify(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @RequestBody CertifyResultsRequest request) {
        return ApiResponse.<CertifyResultsResponse>builder()
                .success(true)
                .message("Results certified")
                .data(raceResultService.certify(userId, raceId, request))
                .build();
    }

    /** PATCH /{resultId} — inline-edit one result row; writes a version audit (AMENDED). */
    @PatchMapping("/{resultId}")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<UpdateResultResponse> updateResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @PathVariable UUID resultId,
            @RequestBody UpdateResultRequest request) {
        return ApiResponse.<UpdateResultResponse>builder()
                .success(true)
                .message("Result updated")
                .data(raceResultService.updateResult(userId, raceId, resultId, request))
                .build();
    }
}
