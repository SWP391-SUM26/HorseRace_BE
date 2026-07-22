package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.CertifyResultsRequest;
import com.SWP391.horserace.races.dto.CertifyResultsResponse;
import com.SWP391.horserace.races.dto.RaceResultsResponse;
import com.SWP391.horserace.races.dto.RecordResultsRequest;
import com.SWP391.horserace.races.dto.ResultRowResponse;
import com.SWP391.horserace.races.dto.UpdateResultRequest;
import com.SWP391.horserace.races.dto.UpdateResultResponse;
import com.SWP391.horserace.predictions.service.SettlementService;
import com.SWP391.horserace.races.service.RaceResultService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Slf4j
public class RaceResultController {

    private final RaceResultService raceResultService;
    private final SettlementService settlementService;

    /**
     * POST — bulk upsert the finish order (one result per entry, status PROVISIONAL). ADMIN-only:
     * referees now publish via POST /api/v1/races/{raceId}/report (OTP-gated, CN3).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * PATCH /certify — flip all results + the race to OFFICIAL (FE-v2 §5).
     *
     * <p>"Xác nhận kết quả cuộc đua" is a referee duty, so RACE_REFEREE is allowed here; the
     * service then requires that referee to be CONFIRMED on this very race. ADMIN keeps the
     * unrestricted override used to consolidate reports across a meeting.
     */
    @PatchMapping("/certify")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<CertifyResultsResponse> certify(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @Valid @RequestBody CertifyResultsRequest request) {
        CertifyResultsResponse response = raceResultService.certify(userId, raceId, request);
        // Follow-on, OUTSIDE certify's transaction (it has now committed): settle every pool of the
        // race. Idempotent + retriable — a failure here leaves the race OFFICIAL with unsettled pools,
        // which the admin resettle endpoint and the sweep job will drain. Guard it so a settlement blip
        // can NEVER turn an already-committed certification into an apparent failure to the caller
        // (retrying certify would then hit RACE_NOT_FINISHED — a misleading dead end).
        try {
            settlementService.settleRace(raceId);
        } catch (RuntimeException e) {
            log.error("Settlement after certifying race {} failed; sweep/resettle will retry", raceId, e);
        }
        return ApiResponse.<CertifyResultsResponse>builder()
                .success(true)
                .message("Results certified")
                .data(response)
                .build();
    }

    /**
     * PATCH /{resultId}/inquiry — flag one result as UNDER_REVIEW (FR-19). Blocks certification until
     * resolved. Rejected once the result is OFFICIAL.
     */
    @PatchMapping("/{resultId}/inquiry")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<Void> flagUnderReview(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @PathVariable UUID resultId) {
        raceResultService.flagUnderReview(userId, raceId, resultId);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Result flagged under review")
                .build();
    }

    /**
     * PATCH /{resultId} — inline-edit one result row; writes a version audit (AMENDED). ADMIN-only:
     * the referee's report is locked after their one-time publish (FR-15 / RT-CRITICAL-2).
     */
    @PatchMapping("/{resultId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UpdateResultResponse> updateResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @PathVariable UUID resultId,
            @Valid @RequestBody UpdateResultRequest request) {
        return ApiResponse.<UpdateResultResponse>builder()
                .success(true)
                .message("Result updated")
                .data(raceResultService.updateResult(userId, raceId, resultId, request))
                .build();
    }

    /** DELETE /{resultId} — remove one provisional result row (and its version history). ADMIN-only. */
    @DeleteMapping("/{resultId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId,
            @PathVariable UUID resultId) {
        raceResultService.deleteResult(userId, raceId, resultId);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Result deleted")
                .build();
    }
}
