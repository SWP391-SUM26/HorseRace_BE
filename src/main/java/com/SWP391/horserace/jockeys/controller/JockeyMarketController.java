package com.SWP391.horserace.jockeys.controller;

import com.SWP391.horserace.jockeys.dto.JockeySuggestionResponse;
import com.SWP391.horserace.jockeys.dto.UnassignedEntryResponse;
import com.SWP391.horserace.jockeys.service.JockeyService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Jockey Market endpoints (FE-v2 §2) that live outside the {@code /jockeys} base path.
 *
 * <ul>
 *   <li>{@code GET /api/v1/owner/unassigned-entries} — the logged-in owner's race entries
 *       that still need a jockey.</li>
 *   <li>{@code GET /api/v1/races/{raceId}/jockey-suggestions?horseId=...} — deterministic
 *       compatibility scores for every active jockey against a race + horse.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class JockeyMarketController {

    private final JockeyService jockeyService;

    /** GET /api/v1/owner/unassigned-entries — owner's entries with no accepted jockey. */
    @GetMapping("/api/v1/owner/unassigned-entries")
    public ApiResponse<List<UnassignedEntryResponse>> getUnassignedEntries(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<UnassignedEntryResponse>>builder()
                .success(true)
                .message("Fetched unassigned entries")
                .data(jockeyService.getUnassignedEntries(userId))
                .build();
    }

    /** GET /api/v1/races/{raceId}/jockey-suggestions?horseId=... — compatibility per jockey. */
    @GetMapping("/api/v1/races/{raceId}/jockey-suggestions")
    public ApiResponse<List<JockeySuggestionResponse>> getJockeySuggestions(
            @PathVariable UUID raceId,
            @RequestParam UUID horseId) {
        return ApiResponse.<List<JockeySuggestionResponse>>builder()
                .success(true)
                .message("Fetched jockey suggestions")
                .data(jockeyService.getJockeySuggestions(raceId, horseId))
                .build();
    }
}
