package com.SWP391.horserace.predictions.controller;

import com.SWP391.horserace.predictions.dto.PoolOddsResponse;
import com.SWP391.horserace.predictions.service.PredictionService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of a race's live pari-mutuel pools. Odds are ESTIMATED (they move as more money
 * enters) and are summed from live bets, not from the pool display counter. Authenticated only.
 */
@RestController
@RequestMapping("/api/v1/races")
@RequiredArgsConstructor
public class PoolController {

    private final PredictionService predictionService;

    @GetMapping("/{raceId}/pools")
    public ApiResponse<List<PoolOddsResponse>> getLivePoolOdds(@PathVariable UUID raceId) {
        return ApiResponse.<List<PoolOddsResponse>>builder()
                .success(true)
                .message("Fetched live estimated pool odds")
                .data(predictionService.getLivePoolOdds(raceId))
                .build();
    }
}
