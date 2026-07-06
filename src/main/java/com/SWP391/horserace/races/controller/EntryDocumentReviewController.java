package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.EntryReviewResponse;
import com.SWP391.horserace.races.dto.RejectEntryRequest;
import com.SWP391.horserace.races.service.EntryDocumentReviewService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Referee's per-race document review of each entry's owner + horse papers (CN2 / FR-09..FR-11).
 * Role-gated to referees/admins; the service additionally enforces that a referee is CONFIRMED for
 * THIS race (RT-CRITICAL-4).
 */
@RestController
@RequestMapping("/api/v1/races/{raceId}")
@PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
@RequiredArgsConstructor
public class EntryDocumentReviewController {

    private final EntryDocumentReviewService entryDocumentReviewService;

    /** GET /api/v1/races/{raceId}/entry-reviews — each entry + owner docs + horse docs + status. */
    @GetMapping("/entry-reviews")
    public ApiResponse<List<EntryReviewResponse>> getEntryReviews(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable("raceId") UUID raceId) {
        return ApiResponse.<List<EntryReviewResponse>>builder()
                .success(true)
                .message("Fetched entry reviews")
                .data(entryDocumentReviewService.getEntryReviews(callerId, raceId))
                .build();
    }

    /** PATCH /api/v1/races/{raceId}/entries/{entryId}/accept — mark documents ACCEPTED. */
    @PatchMapping("/entries/{entryId}/accept")
    public ApiResponse<EntryReviewResponse> acceptEntry(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable("raceId") UUID raceId,
            @PathVariable("entryId") UUID entryId) {
        return ApiResponse.<EntryReviewResponse>builder()
                .success(true)
                .message("Entry documents accepted")
                .data(entryDocumentReviewService.acceptEntry(callerId, raceId, entryId))
                .build();
    }

    /** PATCH /api/v1/races/{raceId}/entries/{entryId}/reject — mark documents REJECTED with a reason. */
    @PatchMapping("/entries/{entryId}/reject")
    public ApiResponse<EntryReviewResponse> rejectEntry(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable("raceId") UUID raceId,
            @PathVariable("entryId") UUID entryId,
            @RequestBody RejectEntryRequest request) {
        return ApiResponse.<EntryReviewResponse>builder()
                .success(true)
                .message("Entry documents rejected")
                .data(entryDocumentReviewService.rejectEntry(callerId, raceId, entryId,
                        request != null ? request.getReason() : null))
                .build();
    }
}
