package com.SWP391.horserace.standings.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.standings.dto.LeaderboardEntryResponse;
import com.SWP391.horserace.standings.service.StandingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * League tables ("bảng xếp hạng") — covers the standings half of the owner, jockey and spectator
 * requirements. Readable by any authenticated user: rankings are public information inside the
 * platform, and the underlying results are already published.
 */
@RestController
@RequestMapping("/api/v1/standings")
@RequiredArgsConstructor
public class StandingsController {

    private final StandingsService standingsService;

    /** GET /api/v1/standings/jockeys — riders by real wins, then prize money. */
    @GetMapping("/jockeys")
    public ApiResponse<List<LeaderboardEntryResponse>> rankJockeys(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.<List<LeaderboardEntryResponse>>builder()
                .success(true)
                .message("Fetched jockey standings")
                .data(standingsService.rankJockeys(limit))
                .build();
    }

    /** GET /api/v1/standings/horses — optionally scoped to one tournament and/or one owner. */
    @GetMapping("/horses")
    public ApiResponse<List<LeaderboardEntryResponse>> rankHorses(
            @RequestParam(value = "tournamentId", required = false) UUID tournamentId,
            @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.<List<LeaderboardEntryResponse>>builder()
                .success(true)
                .message("Fetched horse standings")
                .data(standingsService.rankHorses(tournamentId, ownerUserId, limit))
                .build();
    }

    /** GET /api/v1/standings/predictors — bettors by winnings actually paid out. */
    @GetMapping("/predictors")
    public ApiResponse<List<LeaderboardEntryResponse>> rankPredictors(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.<List<LeaderboardEntryResponse>>builder()
                .success(true)
                .message("Fetched predictor standings")
                .data(standingsService.rankPredictors(limit))
                .build();
    }
}
