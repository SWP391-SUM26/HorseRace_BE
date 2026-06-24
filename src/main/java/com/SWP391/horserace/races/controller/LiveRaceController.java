package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.LiveRaceResponse;
import com.SWP391.horserace.races.service.LiveRaceService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import com.SWP391.horserace.races.dto.UpdateLivePositionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Live race monitor — polling snapshot (FE-v2 §4).
 *
 * <p>Any authenticated caller may poll; the contract imposes no role restriction.
 * Realtime push is a future WebSocket TODO (see {@code LiveRaceServiceImpl}).
 */
@RestController
@RequestMapping("/api/v1/races/{raceId}/live")
@RequiredArgsConstructor
public class LiveRaceController {

    private final LiveRaceService liveRaceService;

    /** GET — current live snapshot (clock, telemetry, running order) for one race. */
    @GetMapping
    public ApiResponse<LiveRaceResponse> getLive(@PathVariable UUID raceId) {
        return ApiResponse.<LiveRaceResponse>builder()
                .success(true)
                .message("Fetched live race snapshot")
                .data(liveRaceService.getLive(raceId))
                .build();
    }

    /** PATCH — update live positions and telemetry data for a race. */
    @PatchMapping
    public ApiResponse<Void> updateLivePositions(
            @PathVariable UUID raceId,
            @Valid @RequestBody UpdateLivePositionRequest request) {
        liveRaceService.updateLivePositions(raceId, request);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Live positions updated successfully")
                .build();
    }

    /** GET — current live leaderboard (running order) for one race. */
    @GetMapping("/leaderboard")
    public ApiResponse<java.util.List<LiveRaceResponse.RunnerRow>> getLiveLeaderboard(@PathVariable UUID raceId) {
        return ApiResponse.<java.util.List<LiveRaceResponse.RunnerRow>>builder()
                .success(true)
                .message("Fetched live leaderboard")
                .data(liveRaceService.getLiveLeaderboard(raceId))
                .build();
    }
}
